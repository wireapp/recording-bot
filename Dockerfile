FROM maven:3-openjdk-11 AS build
LABEL description="Wire Recording bot"
LABEL project="wire-bots:recording"

WORKDIR /app

# download dependencies
COPY pom.xml ./
RUN mvn verify --fail-never -U

# build
COPY . ./
RUN mvn -Dmaven.test.skip=true package

# runtime stage
FROM wirebot/runtime:1.2.0

RUN mkdir /opt/recording
RUN mkdir /opt/recording/assets
RUN mkdir /opt/recording/avatars
RUN mkdir /opt/recording/html

COPY --from=build /app/src/main/resources/assets/* /opt/recording/assets/

WORKDIR /opt/recording

EXPOSE  8080 8081

# Copy configuration
COPY recording.yaml /opt/recording/

# Copy built target
COPY --from=build /app/target/recording.jar /opt/recording/

# create version file
ARG release_version=development
ENV RELEASE_FILE_PATH=/opt/recording/release.txt
RUN echo $release_version > $RELEASE_FILE_PATH

EXPOSE  8080 8081
ENTRYPOINT ["java", "-jar", "recording.jar", "server", "/opt/recording/recording.yaml"]
