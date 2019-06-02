
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
toxiproxy-cli create convergnece_ws -l localhost:9080 -u localhost:8080
toxiproxy-cli create convergnece_rest -l localhost:9081 -u localhost:8081
```

# Add a toxic
```
toxiproxy-cli toxic add convergnece_ws -t latency -a latency=3000
```

# Remove a toxic
```
toxiproxy-cli toxic remove convergnece_ws -n latency_downstream
```

