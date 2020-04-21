# REST API Testing with CURL

## Authenticate
```shell script
curl -H "Origin: http://example.com" \
     -d '{"username": "admin", "password": "password"}' -H 'Content-Type: application/json'  \
     --verbose \
     -X POST \
     http://localhost:8081/auth/login
```

## Validate the session token.
```shell script
curl -H "Origin: http://example.com" \
     -d '{"token": "4Uoh3SpHcpZOW4iTazzZWp5KmKTz332H"}' -H 'Content-Type: application/json'  \
     --verbose \
     -X POST \
     http://localhost:8081/auth/validate
```

## A Simple GET request with the session token header
```shell script
curl -H "Origin: http://example.com" \
     -H "Authorization: SessionToken 4Uoh3SpHcpZOW4iTazzZWp5KmKTz332H" \
     --verbose \
     -X GET \
     http://localhost:8081/user/apiKeys
```


# Make a CORS Pre-Flight Reques
```shell script
curl -H "Origin: http://example.com" \
  -H "Access-Control-Request-Method: POST" \
  -H "Access-Control-Request-Headers: X-Requested-With" \
  -X OPTIONS \
  --verbose \
  http://localhost:8081/auth/login
```