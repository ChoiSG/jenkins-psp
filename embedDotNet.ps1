# https://ppn.snovvcrash.rocks/pentest/infrastructure/ad/av-edr-evasion/dotnet-reflective-assembly

param($inputFile, $outputFile, $templatePath, $toolName)

#$inputFile = $args[0]
#$outputFile = $args[1]
#$templatePath = $args[2]
#$toolName = $args[3]

echo "[inject] inputFile: $inputFile"
echo "[inject] outputFile: $outputFile"
echo "[inject] templateFile: $templatePath"
echo "[inject] toolName: $toolName"

# Compress 
$bytes = [System.IO.File]::ReadAllBytes($inputFile)
[System.IO.MemoryStream] $memStream = New-Object System.IO.MemoryStream
$gzipStream = New-Object System.IO.Compression.GZipStream($memStream, [System.IO.Compression.CompressionMode]::Compress)
$gzipStream.Write($bytes, 0, $bytes.Length)
$gzipStream.Close()
$memStream.Close() 
[byte[]] $byteOutArray = $memStream.ToArray() 
$encodedZipped = [System.Convert]::ToBase64String($byteOutArray)

# Copy/Paste the compress+base64'ed .NET assembly to the template 
Copy-Item -Path $templatePath -Destination $outputFile -Force 

$templateContent = Get-Content -Path $outputFile 
$newContent = $templateContent -replace 'PLACEHOLDER', $encodedZipped
$newContent = $newContent -replace 'TOOLNAME', $toolName 

Set-Content -Path $outputFile -Value $newContent
