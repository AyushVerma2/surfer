# VERSION:      0.1
# DESCRIPTION:	surfer
# AUTHOR:	DEX
# COMMENTS:
#	This runs surfer in a container
# USAGE:
#       docker run -p 8080:8080 surfer:latest

FROM alpine:3.9.2

RUN apk update
RUN apk add bash
RUN apk add curl
RUN apk add openjdk11 --repository=http://dl-cdn.alpinelinux.org/alpine/edge/community

RUN curl -O https://download.clojure.org/install/linux-install-1.10.1.492.sh
RUN chmod +x linux-install-1.10.1.492.sh
RUN ./linux-install-1.10.1.492.sh

WORKDIR /usr/src/app

COPY . .

EXPOSE 3030

ENTRYPOINT ["clojure", "-M:main"]
