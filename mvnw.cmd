@echo off
setlocal
set "DIR=%~dp0"
"%DIR%apache-maven-3.9.6\bin\mvn.cmd" %*
