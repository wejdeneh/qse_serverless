# Use an official Maven image with JDK 17 on Ubuntu 22.04
FROM ubuntu:22.04

# Update packages and install Java, Maven, and necessary fonts
RUN apt-get update && \
    apt-get install -y openjdk-17-jre openjdk-17-jdk-headless maven fontconfig fonts-dejavu && \
    apt-get clean
    

ENV JAVA_HOME /usr/lib/jvm/java-17-openjdk-amd64
ENV PATH $JAVA_HOME/bin:$PATH

# Install Azure Functions Core Tools
RUN apt-get update && \
    apt-get install -y wget && \
    wget -q https://packages.microsoft.com/config/ubuntu/20.04/packages-microsoft-prod.deb -O packages-microsoft-prod.deb && \
    dpkg -i packages-microsoft-prod.deb && \
    apt-get update && \
    apt-get install -y azure-functions-core-tools

# Install Azure CLI for managing Azure Storage (optional if you need CLI tools)
RUN apt-get update && \
    apt-get install -y curl gnupg lsb-release && \
    curl -sL https://packages.microsoft.com/keys/microsoft.asc | gpg --dearmor | tee /etc/apt/trusted.gpg.d/microsoft.asc.gpg > /dev/null && \
    AZ_REPO=$(lsb_release -cs) && \
    echo "deb [arch=amd64] https://packages.microsoft.com/repos/azure-cli/ $AZ_REPO main" | tee /etc/apt/sources.list.d/azure-cli.list && \
    apt-get update && \
    apt-get install -y azure-cli


# Set the working directory
WORKDIR /app

# Copy the Maven project files
COPY pom.xml .
COPY src ./src
COPY script.sh .
RUN chmod +x script.sh

CMD ["./script.sh"]