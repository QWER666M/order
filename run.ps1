# 用 Maven 编译，然后运行程序
Set-Location $PSScriptRoot

mvn -q compile
if (-not $?) {
    Write-Host "编译失败，请先看上面的错误信息" -ForegroundColor Red
    exit 1
}

java -Dstdout.encoding=UTF-8 -Dstdin.encoding=UTF-8 -cp target\classes com.example.order.Main

