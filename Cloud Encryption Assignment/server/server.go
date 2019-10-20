package main

import(
  "../helper"
  "fmt"
  "net"
  "os"
  "log"
  "path"
  "bufio"
  "net/http"
  "io/ioutil"
  "crypto/sha256"
  "encoding/json"
  "math/big"
  "crypto/rsa"
  "crypto/rand"

  "golang.org/x/net/context"
  "golang.org/x/oauth2"
  "golang.org/x/oauth2/google"
  "google.golang.org/api/drive/v3"
)

const (
  FILEFOLDER = "files/" //temp file folder for Google Drive API
  KEYFOLDER = "keys/" //folder to store encrypted AES keys
)

var(
  users map[string]helper.UserDetails //user details indexed by username
  files map[string]string //filename, id
  privKey *rsa.PrivateKey //server's private key
)

func launch_server(){
  //launch server on specified port
  listener, err := net.Listen("tcp", helper.PORT)
  if err != nil {
    log.Fatalf("Unable to start up server, error: %v", err)
  }
  for { //accept incoming connections and handle them concurrently
    connection, err := listener.Accept()
    if err == nil {
      go maintainConn(connection)
    }
  }
}

func handleConnection(connection net.Conn) bool{
  isSuccess := true
  input := helper.ReadResponse(connection) //read message from client
  var message helper.Message
  err := json.Unmarshal(input, &message) //store message as Message struct
  if err != nil {
    return false
  }

  var responseMessage helper.Message
  _, userExists := users[message.Username]
  if userExists || message.MessageType == "register" { //if valid/new user
      switch message.MessageType {
      case "register":
        if handleRegister(message, connection.RemoteAddr()) {
          //respond to register message with server's public key
          responseMessage = helper.Message{MessageType: "register", PublicKeyN: (*(privKey.N)).String(), PublicKeyE: privKey.E}
        } else {
          responseMessage = helper.Message{MessageType: "error"}
        }
      case "file":
        if handleFile(message) {
          //tell client the file has been uploaded
          responseMessage = helper.Message{MessageType: "fileSuccess"}
        } else {
          responseMessage = helper.Message{MessageType: "error"}
        }
      case "fileRequest":
        var ok bool
        responseMessage, ok = handleFileRequest(message)
        if !ok {
          responseMessage = helper.Message{MessageType: "error"}
        }
      }
  } else {
    //reject requests from removed users
    responseMessage = helper.Message{MessageType: "invalidUser"}
    isSuccess = false
  }

  jsonBytes, jsonErr := json.Marshal(responseMessage)
  if jsonErr != nil {
    return false
  }
  _, err = connection.Write(jsonBytes)
  if err != nil {
    return false
  }

  return isSuccess
}

//store a new user's details, esp. their public key
func handleRegister(message helper.Message, address net.Addr) bool{
  var pubKeyN *big.Int = new(big.Int)
  pubKeyN, _ = pubKeyN.SetString(message.PublicKeyN, 10)
  userDets := helper.UserDetails{address, rsa.PublicKey{pubKeyN, message.PublicKeyE} }
  _, ok := users[message.Username]
  if !ok {
    users[message.Username] = userDets
  }
  return !ok
}

//prepare the received file to be uploaded to Google Drive
//Drive API reads directly from disk, so the file must be saved first
func handleFile(message helper.Message) bool{
  encryptedFile := message.EncryptedFile
  encryptedKey := message.EncryptedAESKey

  err := ioutil.WriteFile(FILEFOLDER + message.Filename, encryptedFile, 0644)
  if err != nil {
    return false
  }
  uploadToDrive(FILEFOLDER + message.Filename)
  err = ioutil.WriteFile(KEYFOLDER + message.Filename, encryptedKey, 0644)
  if err != nil {
    return false
  }
  return true
}

//download the requested file, decrypt it (RSA) then encrypt it
//with the client's public key
func handleFileRequest(message helper.Message) (helper.Message, bool) {
  userDets := users[message.Username]
  userPubKey := userDets.PublicKey
  filename := message.Filename

  encryptedFile := downloadFromDrive(files[filename])
  if encryptedFile == nil {
    return helper.Message{MessageType: "error"}, false
  }
  encryptedKey, err := ioutil.ReadFile(KEYFOLDER + filename)
  if err != nil {
    return helper.Message{MessageType: "error"}, false
  }

  decryptedKey, err := rsa.DecryptOAEP(sha256.New(), rand.Reader, privKey, encryptedKey, nil)
  if err != nil {
    return helper.Message{MessageType: "error"}, false
  }

  rsaMaxLength := (privKey.PublicKey.N.BitLen() + 7) / 8 //calc max size of RSA block
  numRSAs := len(encryptedFile) / rsaMaxLength
  if len(encryptedFile) % rsaMaxLength != 0 {
    numRSAs++
  }
  aesEncryptedFile := make([]byte, 0)
  rsaIndex := 0
  for ; rsaIndex < numRSAs - 1; rsaIndex++ {
    tempRSA, err := rsa.DecryptOAEP(sha256.New(), rand.Reader, privKey, encryptedFile[(rsaIndex * rsaMaxLength):((rsaIndex + 1) * rsaMaxLength)], nil)
    if err != nil {
      return helper.Message{MessageType: "error"}, false
    }
    aesEncryptedFile = append(aesEncryptedFile, tempRSA...)
  }

  tempRSA, err := rsa.DecryptOAEP(sha256.New(), rand.Reader, privKey, encryptedFile[(rsaIndex * rsaMaxLength):], nil)
  aesEncryptedFile = append(aesEncryptedFile, tempRSA...)
  if err != nil {
    return helper.Message{MessageType: "error"}, false
  }

  userEncrKey, err := rsa.EncryptOAEP(sha256.New(), rand.Reader, &userPubKey, decryptedKey, nil)
  if err != nil {
    return helper.Message{MessageType: "error"}, false
  }

  rsaMaxLength = ((userPubKey.N.BitLen() + 7) / 8) - (2 * helper.HASHLENGTH) - 2
  numRSAs = len(aesEncryptedFile) / rsaMaxLength
  if len(aesEncryptedFile) % rsaMaxLength != 0 {
    numRSAs++
  }
  userEncrFile := make([]byte, 0)
  rsaIndex = 0
  for ; rsaIndex < numRSAs - 1; rsaIndex++ {
    tempRSA, err = rsa.EncryptOAEP(sha256.New(), rand.Reader, &userPubKey, aesEncryptedFile[(rsaIndex * rsaMaxLength):((rsaIndex + 1) * rsaMaxLength)], nil)
    if err != nil {
      return helper.Message{MessageType: "error"}, false
    }
    userEncrFile = append(userEncrFile, tempRSA...)
  }

  tempRSA, err = rsa.EncryptOAEP(sha256.New(), rand.Reader, &userPubKey, aesEncryptedFile[(rsaIndex * rsaMaxLength):], nil)
  userEncrFile = append(userEncrFile, tempRSA...)
  if err != nil {
    return helper.Message{MessageType: "error"}, false
  }

  fileMessage := helper.Message{MessageType: "file", EncryptedAESKey: userEncrKey, EncryptedFile: userEncrFile, Filename: filename}
  return fileMessage, true
}

// Retrieve a token, saves the token, then returns the generated client.
func getClient(config *oauth2.Config) *http.Client {
        // The file token.json stores the user's access and refresh tokens, and is
        // created automatically when the authorization flow completes for the first
        // time.
        tokFile := "token.json"
        tok, err := tokenFromFile(tokFile)
        if err != nil {
                tok = getTokenFromWeb(config)
                saveToken(tokFile, tok)
        }
        return config.Client(context.Background(), tok)
}

// Request a token from the web, then returns the retrieved token.
func getTokenFromWeb(config *oauth2.Config) *oauth2.Token {
        authURL := config.AuthCodeURL("state-token", oauth2.AccessTypeOffline)
        fmt.Printf("Go to the following link in your browser then type the "+
                "authorization code: \n%v\n", authURL)

        var authCode string
        if _, err := fmt.Scan(&authCode); err != nil {
                log.Fatalf("Unable to read authorization code %v", err)
        }

        tok, err := config.Exchange(context.TODO(), authCode)
        if err != nil {
                log.Fatalf("Unable to retrieve token from web %v", err)
        }
        return tok
}

// Retrieves a token from a local file.
func tokenFromFile(file string) (*oauth2.Token, error) {
        f, err := os.Open(file)
        if err != nil {
                return nil, err
        }
        defer f.Close()
        tok := &oauth2.Token{}
        err = json.NewDecoder(f).Decode(tok)
        return tok, err
}

// Saves a token to a file path.
func saveToken(path string, token *oauth2.Token) {
        f, err := os.OpenFile(path, os.O_RDWR|os.O_CREATE|os.O_TRUNC, 0600)
        if err != nil {
                log.Fatalf("Unable to cache oauth token: %v", err)
        }
        defer f.Close()
        json.NewEncoder(f).Encode(token)
}

//use Google's API to upload the encrypted file to Google Drive
func uploadToDrive(filename string){
  b, err := ioutil.ReadFile("credentials.json")
  if err != nil {
          log.Fatalf("Unable to read client secret file: %v", err)
  }

  config, err := google.ConfigFromJSON(b, drive.DriveFileScope)
  if err != nil {
          log.Fatalf("Unable to parse client secret file to config: %v", err)
  }
  client := getClient(config)

  srv, err := drive.New(client)
  if err != nil {
          log.Fatalf("Unable to retrieve Drive client: %v", err)
  }

  goFile, err := os.Open(filename) // get temp saved file
  //upload to Drive
  driveFile, err := srv.Files.Create(&drive.File{Name: path.Base(filename)}).Media(goFile).Do()
  if err != nil {
          log.Fatalf("Unable to upload file to Google Drive: %v", err)
  }
  files[path.Base(filename)] = driveFile.Id //store file ID for future downloads
  err = os.Remove(filename) //remove temp saved file
}

//download from Google Drive
func downloadFromDrive(fileId string) []byte{
  b, err := ioutil.ReadFile("credentials.json")
  if err != nil {
          log.Fatalf("Unable to read client secret file: %v", err)
  }

  config, err := google.ConfigFromJSON(b, drive.DriveFileScope)
  if err != nil {
          log.Fatalf("Unable to parse client secret file to config: %v", err)
  }
  client := getClient(config)

  srv, err := drive.New(client)
  if err != nil {
          log.Fatalf("Unable to retrieve Drive client: %v", err)
  }

  getDriveFile, err := srv.Files.Get(fileId).Download()
  if err != nil {
    return nil
  }
  resp := helper.ReadDriveResponse(getDriveFile.Body)
  if err != nil {
          log.Fatalf("Unable to download file to Google Drive: %v", err)
  }

  return resp
}

//maintain a connection with each client
func maintainConn(connection net.Conn){
  connUp := true
  for connUp {
    connUp = handleConnection(connection)
  }
}

func main(){
  users = make(map[string]helper.UserDetails)
  files = make(map[string]string)
  privKey = helper.GenerateRSAKeys()
  go launch_server()
  scanner := bufio.NewScanner(os.Stdin)
  for {
    fmt.Println("Enter a user to remove: ")
    scanner.Scan()
    user := scanner.Text()
    delete(users, user) //delete users
  }
}
