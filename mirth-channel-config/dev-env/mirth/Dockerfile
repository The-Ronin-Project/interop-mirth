FROM nextgenhealthcare/connect:4.4.0
USER root
RUN apt-get update && apt-get install sudo
RUN adduser mirth sudo
RUN echo '%sudo ALL=(ALL) NOPASSWD:ALL' >> /etc/sudoers
RUN echo $' \n\
-----BEGIN CERTIFICATE-----\n\
MIIDqDCCApCgAwIBAgIEPhwe6TANBgkqhkiG9w0BAQsFADBiMRswGQYDVQQDDBJ3\n\
d3cubW9ja3NlcnZlci5jb20xEzARBgNVBAoMCk1vY2tTZXJ2ZXIxDzANBgNVBAcM\n\
BkxvbmRvbjEQMA4GA1UECAwHRW5nbGFuZDELMAkGA1UEBhMCVUswIBcNMTYwNjIw\n\
MTYzNDE0WhgPMjExNzA1MjcxNjM0MTRaMGIxGzAZBgNVBAMMEnd3dy5tb2Nrc2Vy\n\
dmVyLmNvbTETMBEGA1UECgwKTW9ja1NlcnZlcjEPMA0GA1UEBwwGTG9uZG9uMRAw\n\
DgYDVQQIDAdFbmdsYW5kMQswCQYDVQQGEwJVSzCCASIwDQYJKoZIhvcNAQEBBQAD\n\
ggEPADCCAQoCggEBAPGORrdkwTY1H1dvQPYaA+RpD+pSbsvHTtUSU6H7NQS2qu1p\n\
sE6TEG2fE+Vb0QIXkeH+jjKzcfzHGCpIU/0qQCu4RVycrIW4CCdXjl+T3L4C0I3R\n\
mIMciTig5qcAvY9P5bQAdWDkU36YGrCjGaX3QlndGxD9M974JdpVK4cqFyc6N4gA\n\
Onys3uS8MMmSHTjTFAgR/WFeJiciQnal+Zy4ZF2x66CdjN+hP8ch2yH/CBwrSBc0\n\
ZeH2flbYGgkh3PwKEqATqhVa+mft4dCrvqBwGhBTnzEGWK/qrl9xB4mTs4GQ/Z5E\n\
8rXzlvpKzVJbfDHfqVzgFw4fQFGV0XMLTKyvOX0CAwEAAaNkMGIwHQYDVR0OBBYE\n\
FH3W3sL4XRDM/VnRayaSamVLISndMA8GA1UdEwEB/wQFMAMBAf8wCwYDVR0PBAQD\n\
AgG2MCMGA1UdJQQcMBoGCCsGAQUFBwMBBggrBgEFBQcDAgYEVR0lADANBgkqhkiG\n\
9w0BAQsFAAOCAQEAecfgKuMxCBe/NxVqoc4kzacf9rjgz2houvXdZU2UDBY3hCs4\n\
MBbM7U9Oi/3nAoU1zsA8Rg2nBwc76T8kSsfG1TK3iJkfGIOVjcwOoIjy3Z8zLM2V\n\
YjYbOUyAQdO/s2uShAmzzjh9SV2NKtcNNdoE9e6udvwDV8s3NGMTUpY5d7BHYQqV\n\
sqaPGlsKi8dN+gdLcRbtQo29bY8EYR5QJm7QJFDI1njODEnrUjjMvWw2yjFlje59\n\
j/7LBRe2wfNmjXFYm5GqWft10UJ7Ypb3XYoGwcDac+IUvrgmgTHD+E3klV3SUi8i\n\
Gm5MBedhPkXrLWmwuoMJd7tzARRHHT6PBH/ZGw==\n\
-----END CERTIFICATE-----' > /usr/local/share/ca-certificates/MockCertificate.crt
RUN update-ca-certificates
RUN keytool -import -file /usr/local/share/ca-certificates/MockCertificate.crt -storepass changeit -cacerts -alias Mock -noprompt