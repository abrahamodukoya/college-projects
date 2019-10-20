package helper

import(
  "net"
  "log"
  "io"
  "crypto/rsa"
  "crypto/rand"
)

const(
  AESKEYANDBLOCKSIZE = 16 //in bytes
  RSAKEYSIZE = 3072 //in bits
  HASHLENGTH = 32 //in bytes
  BUFFERSIZE = 1024 //in bytes
  PORT = ":8000" //server port
  HOST = "localhost" //server host
)

//struct used with JSON
type Message struct {
  MessageType string //register, file, error, fileSuccess, fileRequest, invalidUser
  Username string
  PublicKeyN string
  PublicKeyE int
  EncryptedAESKey []byte
  EncryptedFile []byte
  Filename string
}

type UserDetails struct {
  Address net.Addr
  PublicKey rsa.PublicKey //user public key
}

//read byte from a connection until no more data is being sent
func ReadResponse(connection net.Conn) []byte {
  totalBytesRead := 0
  var input []byte = make([]byte, BUFFERSIZE)
  bytesRead, err := connection.Read(input)
  if err != nil {
    log.Printf("Unable to read from connection: %v, error: %v, ", connection, err)
    return make([]byte, 0)
  }
  input = input[:bytesRead]
  totalBytesRead += bytesRead
  for bytesRead == BUFFERSIZE {
    var newInput []byte = make([]byte, BUFFERSIZE)
    bytesRead, err = connection.Read(newInput)
    if err != nil {
      log.Printf("Unable to read from connection: %v, error: %v, ", connection, err)
      return make([]byte, 0)
    }
    newInput = newInput[:bytesRead]
    totalBytesRead += bytesRead
    input = append(input, newInput...)
  }
  return input
}

//read data from Google Drive until no more is being sent
func ReadDriveResponse(connection io.Reader) []byte {
  totalBytesRead := 0
  var input []byte = make([]byte, BUFFERSIZE)
  bytesRead, err := connection.Read(input)
  if err != nil {
    log.Printf("Unable to read from connection: %v, error: %v, ", connection, err)
    return make([]byte, 0)
  }
  input = input[:bytesRead]
  totalBytesRead += bytesRead
  for bytesRead == BUFFERSIZE {
    var newInput []byte = make([]byte, BUFFERSIZE)
    bytesRead, err = connection.Read(newInput)
    if err != nil {
      log.Printf("Unable to read from connection: %v, error: %v, ", connection, err)
      return make([]byte, 0)
    }
    newInput = newInput[:bytesRead]
    totalBytesRead += bytesRead
    input = append(input, newInput...)
  }
  return input
}

//generate a random AES key
func GenerateAESKey() []byte {
  key := make([]byte, AESKEYANDBLOCKSIZE)
  _, err := rand.Read(key)
  if err != nil {
    log.Printf("Unable to generate AES key, error: %v, ", err)
    key = nil
  }
  return key
}

//generate an RSA key pair
func GenerateRSAKeys() *rsa.PrivateKey {
  privKey, err := rsa.GenerateKey(rand.Reader, RSAKEYSIZE)
  if err != nil {
    log.Printf("Unable to generate RSA keypair, error: %v, ", err)
    privKey = nil
  }
  return privKey
}

//get the length of a null terminated string stored as a byte array
func Clen(n []byte) int {
    for i := 0; i < len(n); i++ {
        if n[i] == 0 {
            return i
        }
    }
    return len(n)
}
