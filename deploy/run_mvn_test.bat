@echo off
set JAVA_HOME=D:\Java\jdk17\azul-17.0.13
set PATH=%JAVA_HOME%\bin;D:\Dev\Maven\apache-maven-3.9.12\bin;%PATH%
cd /d D:\myProject\myGit\fresh-distribution_dsh
call D:\Dev\Maven\apache-maven-3.9.12\bin\mvn.cmd -pl lin-distribution test -o
