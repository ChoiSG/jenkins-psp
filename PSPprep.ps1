
<#
    The logic in this script is incomplete and frankly horrible. 
#>

param($inputDir, $toolName, $obsToolName)

function updatePublic($file, $value){
    $content = Get-Content -Path $file 

    $targetStrings = (Select-String -Path $file -Pattern $value)

    # Change this 
    if($targetStrings -eq $null){
        echo "[*] None found, moving on to next file."
        continue    # this should be return?
    }

    echo "[*] Working with: $file"

    foreach ($targetString in $targetStrings) {
        if($targetString.Line.Contains("/") -or $targetString.Line.Contains("* ")){
            echo "[*] Comment found. Skipping."
            continue
        }

        $targetStrLine = $targetString.Line
        $targetIndex = $targetStrLine.IndexOf("$value")
        $targetStrLineNum = $targetString.LineNumber

        echo "[*] TargetStrLine: $targetStrLine"

        $accessibility = $targetStrLine.tolower().TrimStart().split(' ')[0].trim()
        echo "[*] accessibility: $accessibility"
        
        # If accessibility already exists, continue since changing that might break everything.
        if ($accessibility.contains('private') -or $accessibility.contains('internal') -or $accessibility.contains('protected') -or $accessibility.contains('public') ){

            $targetStrLine = $targetStrLine.trim()
            
            # Except for Main, Program, or class <toolname>. Change accessibility to public for these. 
            if($targetStrLine.contains("Main") -or $targetStrLine.contains("Program") -or $targetStrLine.contains("class $toolName")){
                echo "[+] Changing Program/Main's accessibility to public."
                $newLine = $targetStrLine -replace $accessibility, "public "
                $content[$targetStrLineNum-1] = $newLine
                Set-Content $file $content
                continue
            }

            continue 
        }
        
        # If accessibility is missing, add public infront of it. ex. class Something -> public class Something
        else{
            echo "[+] Adding public infront of $targetStrLine"

            $update = $targetStrLine.Insert(0,"public ")
            $content[$targetStrLineNum-1] = $update 
        }
    
        Set-Content $file $content
    }   
}

function removeComments ($file){
    $content = Get-content -Path $file 
    
    $newContent = ""
    foreach ($line in $content){
        if ($line.trim().startswith("//")){
            continue 
        }
        $newContent += $line 
        $newContent += "`n"
    }

    set-content $file $newContent
}

function removeExit($file){
    $content = Get-content -Path $file 
    
    $newContent = ""
    foreach ($line in $content){
        if ($line.tolower().contains("environment.exit(")){
            continue 
        }
        $newContent += $line 
        $newContent += "`n"
    }

    set-content $file $newContent
}


###
#   Main 
###

$allFiles = (Get-ChildItem -Path $inputDir -Include "*.cs" -Recurse -ErrorAction SilentlyContinue -Force -Depth 3).FullName
foreach($file in $allFiles){
    removeComments $file
    removeExit $file
}

$files = (Get-ChildItem -Path $inputDir -Include "Program.cs", "*$toolName*.cs" -Recurse -ErrorAction SilentlyContinue -Force -Depth 2).FullName

foreach ($file in $files){
    if ($file.Contains("\obj")){
        continue
    }

    updatePublic $file "class "
    updatePublic $file "main\(string"
}
