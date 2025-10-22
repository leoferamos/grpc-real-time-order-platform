FROM alpine:3.19

# Install OpenSSL for certificate generation
RUN apk add --no-cache openssl bash

# Set working directory
WORKDIR /certs

# Copy certificate generation script and configuration
COPY generate-certs.sh /usr/local/bin/
COPY openssl-server.cnf /etc/ssl/
COPY openssl-client.cnf /etc/ssl/
RUN chmod +x /usr/local/bin/generate-certs.sh

# Run certificate generation script
ENTRYPOINT ["/usr/local/bin/generate-certs.sh"]
