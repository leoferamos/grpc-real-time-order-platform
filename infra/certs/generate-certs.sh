set -e
CERTS_DIR="/certs"
mkdir -p "$CERTS_DIR"
cd "$CERTS_DIR"

# Generate CA key and cert
openssl genrsa -out ca.key 4096
openssl req -x509 -new -nodes -key ca.key -sha256 -days 3650 -out ca.crt -subj "/CN=grpc-ca"

# Generate server key and CSR
openssl genrsa -out server.key 4096
openssl req -new -key server.key -out server.csr -subj "/CN=grpc-server"

# Sign server CSR with CA
openssl x509 -req -in server.csr -CA ca.crt -CAkey ca.key -CAcreateserial -out server.crt -days 3650 -sha256

# Generate client key and CSR
openssl genrsa -out client.key 4096
openssl req -new -key client.key -out client.csr -subj "/CN=grpc-client"

# Sign client CSR with CA
openssl x509 -req -in client.csr -CA ca.crt -CAkey ca.key -CAcreateserial -out client.crt -days 3650 -sha256

# Clean up CSRs
rm -f server.csr client.csr

chmod 600 *.key
chmod 644 *.crt

echo "Certificates generated in $CERTS_DIR:"
ls -l $CERTS_DIR
