package main

import (
	"flag"
	"fmt"
	"io"
	"log"
	"net"
	"net/http"
	"net/url"
	"strings"
	"time"
)

type Proxy struct {
	backend *url.URL
	client  *http.Client
}

func NewProxy(backend *url.URL, timeout time.Duration) *Proxy {
	return &Proxy{
		backend: backend,
		client: &http.Client{
			Timeout: timeout,
		},
	}
}

func (p *Proxy) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	target := p.backend.ResolveReference(r.URL)
	target.Scheme = p.backend.Scheme
	target.Host = p.backend.Host

	proxyReq, err := http.NewRequestWithContext(r.Context(), r.Method, target.String(), r.Body)
	if err != nil {
		http.Error(w, "could not create proxy request", http.StatusInternalServerError)
		return
	}

	copyHeaders(proxyReq.Header, r.Header)
	proxyReq.Host = p.backend.Host
	addForwardedHeaders(proxyReq, r)

	resp, err := p.client.Do(proxyReq)
	if err != nil {
		http.Error(w, "backend request failed", http.StatusBadGateway)
		log.Printf("%s %s -> %s failed: %v", r.Method, r.URL.Path, target, err)
		return
	}
	defer resp.Body.Close()

	copyHeaders(w.Header(), resp.Header)
	w.WriteHeader(resp.StatusCode)

	if _, err := io.Copy(w, resp.Body); err != nil {
		log.Printf("response copy failed: %v", err)
	}

	log.Printf("%s %s -> %s %d", r.Method, r.URL.Path, target, resp.StatusCode)
}

func copyHeaders(dst, src http.Header) {
	for key, values := range src {
		for _, value := range values {
			dst.Add(key, value)
		}
	}
}

func addForwardedHeaders(proxyReq *http.Request, originalReq *http.Request) {
	host, _, err := net.SplitHostPort(originalReq.RemoteAddr)
	if err != nil {
		host = originalReq.RemoteAddr
	}

	if prior := proxyReq.Header.Get("X-Forwarded-For"); prior != "" {
		host = strings.Join([]string{prior, host}, ", ")
	}

	proxyReq.Header.Set("X-Forwarded-For", host)
	proxyReq.Header.Set("X-Forwarded-Host", originalReq.Host)
	proxyReq.Header.Set("X-Forwarded-Proto", scheme(originalReq))
}

func scheme(r *http.Request) string {
	if r.TLS != nil {
		return "https"
	}
	return "http"
}

func main() {
	listenAddr := flag.String("listen", ":8080", "address for the proxy to listen on")
	backendURL := flag.String("backend", "http://localhost:8081", "backend server URL")
	timeout := flag.Duration("timeout", 30*time.Second, "backend request timeout")
	flag.Parse()

	backend, err := url.Parse(*backendURL)
	if err != nil || backend.Scheme == "" || backend.Host == "" {
		log.Fatalf("invalid backend URL %q", *backendURL)
	}

	proxy := NewProxy(backend, *timeout)
	server := &http.Server{
		Addr:              *listenAddr,
		Handler:           proxy,
		ReadHeaderTimeout: 10 * time.Second,
	}

	fmt.Printf("Reverse proxy listening on %s and forwarding to %s\n", *listenAddr, backend)
	if err := server.ListenAndServe(); err != nil && err != http.ErrServerClosed {
		log.Fatal(err)
	}
}
