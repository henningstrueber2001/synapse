[![Build Status](https://travis-ci.com/henningstrueber2001/synapse.svg?branch=master)](https://travis-ci.com/henningstrueber2001/synapse)

# OTTO Synapse
A library to implement event-sourcing microservices.


# Setup for Development

1. Install Docker and LocalStack

Docker is required to run LocalStack, so first you have to [install Docker](https://docs.docker.com/install/).

LocalStack is "A fully functional local AWS cloud stack". Synapse is using LocalStack in order to run integration tests
locally, without having to access the "real" AWS services.

Follow the instructions here: https://github.com/localstack/localstack

2. Setup AWS Region + Credentials

https://docs.aws.amazon.com/sdk-for-java/v1/developer-guide/setup-credentials.html

3. Building Synapse

Run ```./gradlew build```
