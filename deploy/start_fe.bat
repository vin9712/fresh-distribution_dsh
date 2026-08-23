@echo off
set PATH=D:\Dev\NodeJs\v24.14.1;%PATH%
cd /d D:\myProject\myGit\fresh-distribution_dsh\RuoYi-Vue3
start "dsh-frontend" /D D:\myProject\myGit\fresh-distribution_dsh\RuoYi-Vue3 cmd /c "npm run dev > D:\myProject\myGit\fresh-distribution_dsh\logs\frontend.log 2>&1"
