# VERSION:      0.1
# DESCRIPTION:	surfer
# AUTHOR:	DEX
# COMMENTS:
#	This runs surfer in a container
# USAGE:
#       docker run -p 8080:8080 surfer:latest

FROM alpine:3.9.2

RUN apk update && apk add --no-cache \
  procps openjdk8 maven

ARG USER_HOME_DIR="/root"

RUN mkdir -p $USER_HOME_DIR
WORKDIR $USER_HOME_DIR

COPY . $USER_HOME_DIR
RUN rm -rf $USER_HOME_DIR/target

ENV HOME $USER_HOME_DIR
ENV MAVEN_CONFIG "$USER_HOME_DIR/.m2"
ENV SURFER_CREATE_DB 1
ENV HTTP_PORT 8080

RUN free
RUN java -version
RUN mvn -v
RUN mvn clean install

EXPOSE 8080

ENTRYPOINT ["mvn", "exec:java"]
