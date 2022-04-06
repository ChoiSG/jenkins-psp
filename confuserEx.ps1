param($exePath, $outDir, $level, $toolName)

$confuserConf = 
@"
<project outputDir="{{OUTPUT_DIR}}" baseDir="{{BASE_DIR}}" xmlns="http://www.google.com">
    <module path="{{MODULE_PATH}}" >
        <rule pattern="true" preset="{{LEVEL}}" inherit="false">
            <protection id="anti ildasm" />
            <protection id="anti debug" action="remove" /> <!-- this breaks Assembly.Load. Maybe just use donut?  -->
            <protection id="anti dump" action="remove" /> <!-- this breaks sharphound --> 
            <protection id="anti tamper" action="remove" /> <!-- this breaks Assembly.Load. Maybe just use donut?  -->
            <protection id="invalid metadata" />
            <protection id="resources" action="remove" /> <!-- this breaks sharphound --> 
            <protection id="constants" />
            <protection id="ctrl flow" />
            <protection id="typescramble" action="remove" />
            <protection id="rename" action="remove" /> <!-- This just killed seatbelt for some reason --> 
        </rule>
    </module>
</project>
"@

$baseDir = Split-Path $exePath -Parent
$exeFilename = Split-Path $exePath -Leaf

# Modify configuration file with params 
$confuserConf = $confuserConf.
    Replace("{{OUTPUT_DIR}}", $outDir).
    Replace("{{BASE_DIR}}", $baseDir).
    Replace("{{LEVEL}}", $level).
    Replace("{{MODULE_PATH}}", $exeFilename)

$crprojFilename = join-path -path $outDir -childpath "\$toolName.crproj"

if(!(Test-Path $outDir -PathType Container)){
    New-Item -ItemType Directory -Force -Path $outDir
}

echo $confuserConf | Set-Content -path $crprojFilename
echo "[+] ConfuserEx config file written: $crprojFilename"
