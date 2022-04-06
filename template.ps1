function Invoke-TOOLNAME
{
    # PowerSharpPack template from https://github.com/S3cur3Th1sSh1t/PowerSharpPack

    [CmdletBinding()]
    Param (
        [String]
        $Command = ""
    )

    $a = New-Object IO.MemoryStream(,[Convert]::FromBase64String("PLACEHOLDER"))

    $decompressed = New-Object IO.Compression.GzipStream($a,[IO.Compression.CoMPressionMode]::DEComPress)
    $output = New-Object System.IO.MemoryStream
    $decompressed.CopyTo( $output )
    [byte[]] $byteOutArray = $output.ToArray()
    $RAS = [System.Reflection.Assembly]::Load($byteOutArray)

    $OldConsoleOut = [Console]::Out
    $StringWriter = New-Object IO.StringWriter
    [Console]::SetOut($StringWriter)

    [TOOLNAME.Program]::main($Command.Split(" "))

    #$RAS.EntryPoint.Invoke($null, [Object[]]@( ,[String[]]@($Command.Split(" "))))

    [Console]::SetOut($OldConsoleOut)
    $Results = $StringWriter.ToString()
    $Results
}
