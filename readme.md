Lokipender
==========

Quick and dirty Logback appender for [Loki](https://github.com/grafana/loki/tree/master/docs) 
using [gRPC](https://grpc.io/). 


Builds
------
https://jitpack.io/#kimble/lokipender

Status
------
This is just a pet project and should not be used in production. 

 - [ ] Test what happens when the buffer is full and Loki is unavailable, slow or throwing errors 
 - [ ] Append stacktrace to formatted log message
 - [ ] ...