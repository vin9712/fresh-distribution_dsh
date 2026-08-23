@echo off
set JAVA_HOME=D:\Java\jdk17\azul-17.0.13
set PATH=%JAVA_HOME%\bin;D:\Dev\Maven\apache-maven-3.9.12\bin;%PATH%
cd /d D:\myProject\myGit\fresh-distribution_dsh
start "dsh-backend" /D D:\myProject\myGit\fresh-distribution_dsh cmd /c "mvn -pl lin-entry spring-boot:run -Dspring-boot.run.main-class=com.lin.FreshDistributionApplication > D:\myProject\myGit\fresh-distribution_dsh\logs\backend.log 2>&1"
