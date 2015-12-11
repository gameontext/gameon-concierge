# gameon-concierge

[![Codacy Badge](https://api.codacy.com/project/badge/grade/0c29c501ba11477f944e109b85817593)](https://www.codacy.com/app/gameontext/gameon-concierge)

This is a service to manage the various rooms available in a game.  


## Docker for Concierge App

To build a Docker image for this app/service, execute the following:

```
gradle buildImage
```

Or, if you don't have gradle, then:

```
./gradlew buildImage
```

### Interactive Run

```
docker run -it -p 9081:9081 -e LICENSE=accept gameon-concierge bash
```

Then, you can start the server with 
```
/opt/ibm/wlp/bin/server run defaultServer
```

### Daemon Run

```
docker run -d -p 9081:9081 -e LICENSE=accept --name gameon-concierge gameon-concierge
```

### Stop

```
docker stop gameon-concierge ; docker rm gameon-concierge
```

### Restart Daemon

```
docker stop gameon-concierge ; docker rm gameon-concierge ; docker run -d -p 9081:9081 -e LICENSE=accept --name gameon-concierge gameon-concierge
```
