# Java Reverse Proxy

A small reverse proxy implemented with plain Java sockets. It listens on a port, forwards HTTP traffic to a backend server, and returns the response to the client. Each connection runs in its own thread.

## Features
- Forwards HTTP requests to a target server
- Thread-per-connection model
- Two-way piping between client and backend
- No external libraries

## Architecture

```mermaid
graph TD
    A[Start Server on Port] --> B{Wait for Client}
    B -->|Client Connects| C[Open Socket to Target]
    C --> D[Pipe Data <--> Two Ways]
    D -->|Done/Timeout| E[Close Both Sockets]
    E --> B
