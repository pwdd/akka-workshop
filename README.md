# Chakka IAM #

Chakka is an explanatory chat application based upon reactive and autonomous services implemented
with Akka and Scala. Chakka IAM is the service providing identity and access management.

The below examples assume you are using [HTTPie](https://httpie.org), but
[cURL](https://curl.haxx.se) or other clients would work similarly.

## Sing up ##

```text
http :8080/iam/accounts username=alex password=m3g0s nickname=Alex
```

## Sign in ##

```text
http --session=alex :8080/iam/sessions username=alex password=m3g0s
```

## Copyright ##

Copyright 2018 Heiko Seeberger
