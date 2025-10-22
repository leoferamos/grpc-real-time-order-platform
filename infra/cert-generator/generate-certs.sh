set -e
CERTS_DIR="/certs"
CA_KEY="${CERTS_DIR}/ca.key"
CA_CERT="${CERTS_DIR}/ca.crt"
SERVER_KEY="${CERTS_DIR}/server.key"
SERVER_CSR="${CERTS_DIR}/server.csr"
SERVER_CERT="${CERTS_DIR}/server.crt"
CLIENT_KEY="${CERTS_DIR}/client.key"
CLIENT_CSR="${CERTS_DIR}/client.csr"
CLIENT_CERT="${CERTS_DIR}/client.crt"

# Check if certificates already exist
if [ -f "${CA_CERT}" ] && [ -f "${SERVER_CERT}" ] && [ -f "${CLIENT_CERT}" ]; then
    echo "Certificates already exist. Skipping generation."
    ls -lah ${CERTS_DIR}
    exit 0
fi

echo ""
echo "Generating Certificate Authority (CA)"
echo "----------------------------------------------"
openssl genrsa -out ${CA_KEY} 4096
openssl req -new -x509 \
    -key ${CA_KEY} \
    -out ${CA_CERT} \
    -days 3650 \
    -subj '/CN=gRPC-CA/O=Real-Time Order Platform/C=BR'

echo "CA certificate generated"

echo ""
echo "Generating Server Certificate (order-service)"
echo "-------------------------------------------------------"
openssl genrsa -out ${SERVER_KEY} 4096
openssl req -new \
    -key ${SERVER_KEY} \
    -out ${SERVER_CSR} \
    -config /etc/ssl/openssl-server.cnf

openssl x509 -req \
    -in ${SERVER_CSR} \
    -CA ${CA_CERT} \
    -CAkey ${CA_KEY} \
    -CAcreateserial \
    -out ${SERVER_CERT} \
    -days 3650 \
    -extensions v3_req \
    -extfile /etc/ssl/openssl-server.cnf

echo "Server certificate generated with SANs"

echo ""
echo "Generating Client Certificate (gateway-api)"
echo "-----------------------------------------------------"
openssl genrsa -out ${CLIENT_KEY} 4096
openssl req -new \
    -key ${CLIENT_KEY} \
    -out ${CLIENT_CSR} \
    -config /etc/ssl/openssl-client.cnf

openssl x509 -req \
    -in ${CLIENT_CSR} \
    -CA ${CA_CERT} \
    -CAkey ${CA_KEY} \
    -CAcreateserial \
    -out ${CLIENT_CERT} \
    -days 3650 \
    -extensions v3_req \
    -extfile /etc/ssl/openssl-client.cnf

echo "Client certificate generated with SANs"

echo ""
echo "Converting Private Keys to PKCS#8 Format"
echo "--------------------------------------------------"
openssl pkcs8 -topk8 -nocrypt -in ${SERVER_KEY} -out ${SERVER_KEY}.pkcs8
openssl pkcs8 -topk8 -nocrypt -in ${CLIENT_KEY} -out ${CLIENT_KEY}.pkcs8
mv ${SERVER_KEY}.pkcs8 ${SERVER_KEY}
mv ${CLIENT_KEY}.pkcs8 ${CLIENT_KEY}

echo "Private keys converted to PKCS#8 format"

echo ""
echo "Setting Proper Permissions"
echo "-----------------------------------"
chmod 600 ${CA_KEY} ${SERVER_KEY} ${CLIENT_KEY}
chmod 644 ${CA_CERT} ${SERVER_CERT} ${CLIENT_CERT}
echo "Generated files:"
ls -lh ${CERTS_DIR}

echo ""
echo "Verifying certificates..."
echo ""
echo "Server certificate SANs:"
openssl x509 -in ${SERVER_CERT} -noout -text | grep -A 3 "Subject Alternative Name" || echo "  No SANs found"

echo ""
echo "Client certificate SANs:"
openssl x509 -in ${CLIENT_CERT} -noout -text | grep -A 3 "Subject Alternative Name" || echo "  No SANs found"

echo ""
echo "Certificates are ready for use by gRPC services."

