
A quite handy tool for throttling / disabling network resources.

# Installation
See documentation here:

https://github.com/Shopify/toxiproxy

For OSX

```
$ brew tap shopify/shopify
$ brew install toxiproxy
```

# Start Server

```
toxiproxy-server
```


# Create Proxies
```
toxiproxy-cli create convergence_ws -l localhost:9080 -u localhost:8080
toxiproxy-cli create convergence_rest -l localhost:9081 -u localhost:8081
```

# Add a toxic
```
toxiproxy-cli toxic add convergence_ws -t latency -a latency=3000
```

# Remove a toxic
```
toxiproxy-cli toxic remove convergence_ws -n latency_downstream
```

