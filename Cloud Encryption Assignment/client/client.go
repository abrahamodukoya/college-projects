package main

import(
  "../helper"
  "fmt"
  "net"
  "bufio"
  "os"
  "path"
  "log"
  "crypto/sha256"
  "io/ioutil"
  "math/big"
  "crypto/rsa"
  "crypto/aes"
  "crypto/rand"
  "encoding/json"
)

var (
  username string
  privKey *rsa.PrivateKey
  connection net.Conn
  serverPubKey rsa.PublicKey
)

//register a user with the server
func registerWithServer(){
  fmt.Println("Enter in your username: ")
  scanner := bufio.NewScanner(os.Stdin)
  scanner.Scan()
  username = scanner.Text()
  privKey = helper.GenerateRSAKeys()
  regMessage := helper.Message{MessageType: "register", Username: username, PublicKeyN: (*(privKey.N)).String(), PublicKeyE: privKey.E}
  jsonBytes, jsonErr := json.Marshal(regMessage)
  if jsonErr != nil {
    log.Printf("Unable to encode register message: %v, error: %v", regMessage, jsonErr)
    return
  }
  var err error
  connection, err = net.Dial("tcp", helper.HOST + helper.PORT)
  if err != nil {
    log.Fatalf("Unable to connect to server, error: %v", err)
  }
  _, err = connection.Write(jsonBytes)
  if err != nil {
    log.Fatalf("Unable to register with server, error: %v", err)
  }
  response := helper.ReadResponse(connection)
  responseMessage := new(helper.Message)
  json.Unmarshal(response, responseMessage)
  var serverPubKeyN *big.Int = new(big.Int)
  serverPubKeyN, _ = serverPubKeyN.SetString(responseMessage.PublicKeyN, 10)
  serverPubKey = rsa.PublicKey{N: serverPubKeyN, E: responseMessage.PublicKeyE}
  fmt.Println("You have been registered with the server")
}

//encrypt a file with its own AES key and encrypt the file and key with
//the server's public key, then upload them to the server
func uploadFile(filename string){
  fileContents, err := ioutil.ReadFile(filename)
  if err != nil {
    log.Printf("Unable to read file: %v, error: %v", filename, err)
    return
  }

  //pad plaintext according to PKCS#7
  numPadBytes := byte(helper.AESKEYANDBLOCKSIZE - (len(fileContents) % helper.AESKEYANDBLOCKSIZE))
  padBytes := make([]byte, numPadBytes)
  if numPadBytes == 0 {
    numPadBytes = 16
  }
  for i := range padBytes {
    padBytes[i] = numPadBytes
  }
  fileContents = append(fileContents, padBytes...)

  aesKey := helper.GenerateAESKey()
  cipherBlock, err := aes.NewCipher(aesKey)
  if err != nil {
    log.Printf("Unable to make AES cipher, error: %v", err)
    return
  }
  ciphertext := make([]byte, len(fileContents))
  numBlocks := len(fileContents) / aes.BlockSize
  for blockIndex := 0; blockIndex < numBlocks; blockIndex++ {
    cipherBlock.Encrypt(ciphertext[(blockIndex * aes.BlockSize):], fileContents[(blockIndex * aes.BlockSize):])
  }
  rsaMaxLength := ((serverPubKey.N.BitLen() + 7) / 8) - (2 * helper.HASHLENGTH) - 2
  numRSAs := len(ciphertext) / rsaMaxLength
  if len(ciphertext) % rsaMaxLength != 0 {
    numRSAs++
  }
  finalFile := make([]byte, 0)
  rsaIndex := 0
  for ; rsaIndex < numRSAs - 1; rsaIndex++ {
    tempRSA, err := rsa.EncryptOAEP(sha256.New(), rand.Reader, &serverPubKey, ciphertext[(rsaIndex * rsaMaxLength):((rsaIndex + 1) * rsaMaxLength)], nil)
    if err != nil {
      log.Printf("Unable to encrypt file: %v, error: %v", filename, err)
      return
    }
    finalFile = append(finalFile, tempRSA...)
  }

  tempRSA, err := rsa.EncryptOAEP(sha256.New(), rand.Reader, &serverPubKey, ciphertext[(rsaIndex * rsaMaxLength):], nil)
  finalFile = append(finalFile, tempRSA...)
  if err != nil {
    log.Printf("Unable to encrypt file: %v, error: %v", filename, err)
    return
  }
  encryptedKey, err := rsa.EncryptOAEP(sha256.New(), rand.Reader, &serverPubKey, aesKey, nil)
  if err != nil {
    log.Printf("Unable to encrypt key for file: %v, error: %v", filename, err)
    return
  }
  fileMessage := helper.Message{MessageType: "file", Username: username, EncryptedFile: finalFile, EncryptedAESKey: encryptedKey, Filename: path.Base(filename)}
  jsonBytes, jsonErr := json.Marshal(fileMessage)
  if err != nil {
    log.Printf("Unable to encrypt file: %v, error: %v", filename, jsonErr)
    return
  }
  _, err = connection.Write(jsonBytes)
  if err != nil {
    log.Printf("Unable to send message: %v, error: %v", jsonBytes, err)
    return
  }

  responseBytes := helper.ReadResponse(connection)
  var responseMessage helper.Message
  err = json.Unmarshal(responseBytes, &responseMessage)
  if err != nil {
    log.Printf("Unable to parse message: %v, error: %v", responseMessage, err)
    return
  }
  if responseMessage.MessageType == "invalidUser" {
    log.Fatalf("This user has been removed")
  } else if responseMessage.MessageType == "error" {
    log.Printf("Unable to upload file: %v", filename)
  }

}

//download a file from the server, decrypt it and the corresponding AES key
//with this client's private key then decrypt the file with the AES key
func downloadFile(filename string){
  fileMessage := helper.Message{MessageType: "fileRequest", Username: username, Filename: path.Base(filename)}
  jsonBytes, jsonErr := json.Marshal(fileMessage)
  if jsonErr != nil {
    log.Printf("Unable to encode message, error: %v", jsonErr)
    return
  }
  _, err := connection.Write(jsonBytes)
  if err != nil {
    log.Printf("Unable to send message: %v, error: %v", jsonBytes, err)
    return
  }
  responseBytes := helper.ReadResponse(connection)
  var responseMessage helper.Message
  err = json.Unmarshal(responseBytes, &responseMessage)
  if err != nil {
    log.Printf("Unable to parse message: %v, error: %v", responseMessage, err)
    return
  }

  if responseMessage.MessageType == "invalidUser" {
    log.Fatalf("This user has been removed")
  }
  encryptedFile := responseMessage.EncryptedFile
  encryptedKey := responseMessage.EncryptedAESKey

  rsaMaxLength := (privKey.PublicKey.N.BitLen() + 7) / 8
  numRSAs := len(encryptedFile) / rsaMaxLength
  if len(encryptedFile) % rsaMaxLength != 0 {
    numRSAs++
  }
  aesEncryptedFile := make([]byte, 0)
  rsaIndex := 0
  for ; rsaIndex < numRSAs - 1; rsaIndex++ {
    tempRSA, err := rsa.DecryptOAEP(sha256.New(), rand.Reader, privKey, encryptedFile[(rsaIndex * rsaMaxLength):((rsaIndex + 1) * rsaMaxLength)], nil)
    if err != nil {
      log.Printf("Unable to decrypt file: %v, error: %v", filename, err)
      return
    }
    aesEncryptedFile = append(aesEncryptedFile, tempRSA...)
  }

  tempRSA, err := rsa.DecryptOAEP(sha256.New(), rand.Reader, privKey, encryptedFile[(rsaIndex * rsaMaxLength):], nil)
  if err != nil {
    log.Printf("Unable to decrypt file: %v, error: %v", filename, err)
    return
  }
  aesEncryptedFile = append(aesEncryptedFile, tempRSA...)

  decryptedKey, err := rsa.DecryptOAEP(sha256.New(), rand.Reader, privKey, encryptedKey, nil)
  if err != nil {
    log.Printf("Unable to decrypt key for file: %v, error: %v", filename, err)
    return
  }

  cipherBlock, err := aes.NewCipher(decryptedKey)
  if err != nil {
    log.Printf("Unable to make AES cipher, error: %v", err)
    return
  }

  decryptedFile := make([]byte, len(aesEncryptedFile))
  numBlocks := len(decryptedFile) / aes.BlockSize
  for blockIndex := 0; blockIndex < numBlocks; blockIndex++ {
    cipherBlock.Decrypt(decryptedFile[(blockIndex * aes.BlockSize):], aesEncryptedFile[(blockIndex * aes.BlockSize):])
  }
  padVal := decryptedFile[len(decryptedFile) - 1]
  decryptedFile = decryptedFile[:len(decryptedFile) - (int(padVal))]
  fmt.Println(string(decryptedFile))
  err = ioutil.WriteFile(filename, decryptedFile, 0644)
  if err != nil {
    log.Printf("Unable to save file: %v, error: %v", filename, err)
  }
}

func main(){ //let the user upload and download files
  registerWithServer()
  scanner := bufio.NewScanner(os.Stdin)
  for {
    fmt.Println("Upload or download file?: ")
    scanner.Scan()
    choice := scanner.Text()
    if choice == "upload" {
      fmt.Println("Please enter a file to upload: ")
      scanner.Scan()
      file := scanner.Text()
      uploadFile(file)
    } else if choice == "download" {
      fmt.Println("Please enter a file to download: ")
      scanner.Scan()
      file := scanner.Text()
      downloadFile(file)
    }

  }
  defer connection.Close()
}
