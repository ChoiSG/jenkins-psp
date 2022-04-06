pipeline { 
    agent any
    
    parameters {
        string(
            name: 'ProjectID',
            description: "Project ID for the engagement"
        )
    }

    environment { 
        // << CHANGE THESE >>  and DOTNETVERSION
        TOOLNAME = "Certify"
        GITURL = "https://github.com/GhostPack/Certify.git"
        BRANCH = "main"
        WORKDIR = "C:\\opt\\jenkins-psp"

        PSP_OUTPUT = "${WORKDIR}\\Invoke-${TOOLNAME}-${params.ProjectID}.ps1"
        OBS_PSP_OUTPUT = "${WORKDIR}\\Obs-Invoke-${TOOLNAME}-${params.ProjectID}.ps1"

        // << CHANGE THESE >> - .NET Compile configs
        CONFIG="Release"
        PLATFORM="Any CPU"
        DOTNETVERSION="v4.0"
        DOTNETNUMBER="net40"    // net35, net40, net452, net462, net5
        
        // 3rd party tools 
        CHAMELEONPATH = "${WORKDIR}\\chameleon\\chameleon.py"
        EMBEDDOTNETPATH = "${WORKDIR}\\embedDotNet.ps1"
        PREPPSPPATH = "${WORKDIR}\\PSPprep.ps1"
        TEMPLATEPATH = "${WORKDIR}\\template.ps1"
        CONFUSERPREP = "${WORKDIR}\\confuserEx.ps1"
    }

    stages {
        stage('Cleanup'){
            steps{
                deleteDir()
                dir("${TOOLNAME}"){
                    deleteDir()
                }
            }
        }

        stage('Git-Clone'){
            steps{
                script {     
                    checkout([
                        $class: 'GitSCM',
                        branches: [[name: "*/${BRANCH}"]],
                        userRemoteConfigs: [[url: "${GITURL}"]]
                    ]) 
                }
            }
        }

        // Skip prep powersharppack if the tool already has public class/main function.
        stage('Prep-PSP'){
            steps{
                powershell "${PREPPSPPATH} -inputDir ${WORKSPACE} -toolName ${TOOLNAME}"
            }
        }

        // Try nuget, then dotnet (for net5.0++)
        // TODO: what's this "msbuild PROJECT.sln /t:Restore /p:Configuration=Release"
        stage('Nuget-Restore'){
            steps{
                script{
                    def slnPath = powershell(returnStdout: true, script: "(Get-ChildItem -Path ${WORKSPACE} -Include '${TOOLNAME}.sln' -Recurse).FullName")
                    env.SLNPATH = slnPath
                    
                    try{ 
                        bat "nuget restore ${SLNPATH}"
                    }
                    catch(Exception e){
                        catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE') {
                            bat """dotnet restore ${SLNPATH} """
                        }
                    }
                }
            }
        }

        // Try msbuild, then dotnet (for net5.0++). TODO: Find dotnet build params for pdb removal and release.
        stage('Compile'){ 
            steps {
                script{ 
                    try{
                        bat "\"${tool 'MSBuild_VS2019'}\\MSBuild.exe\" /p:Configuration=${CONFIG} \"/p:Platform=${PLATFORM}\" /maxcpucount:%NUMBER_OF_PROCESSORS% /nodeReuse:false /p:DebugType=None /p:DebugSymbols=false /p:TargetFrameworkMoniker=\".NETFramework,Version=${DOTNETVERSION}\" ${SLNPATH}" 
                    }   
                    catch(Exception e){
                        catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE') {
                            bat """dotnet build ${SLNPATH} """ 
                        }
                    }      
                }
            }
        }

       // ConfuserEx only when it's not net5.0++.
        stage('ConfuserEx'){
            when {
                expression { env.DOTNETVERSION != 'net5.0'} 
            }
            steps{
                script{ 
                    // Some projects have net45/net35, some projects doesn't so many one-offs        
                    def exePath = powershell(returnStdout: true, script: """
                    \$exeFiles = (Get-ChildItem -Path ${WORKSPACE} -Include '*.exe' -Recurse | Where-Object {\$_.DirectoryName -match 'release' -and \$_.DirectoryName -match 'bin' } ).FullName
                    if (\$exeFiles -match "${DOTNETNUMBER}"){
                        \$exeFiles -match "${DOTNETNUMBER}"
                    }
                    else{
                        (Get-ChildItem -Path ${WORKSPACE} -Include '*.exe' -Recurse | Where-Object {\$_.DirectoryName -match 'release'} )[0].FullName
                    }
                    """)
                    env.EXEPATH = exePath

                    // Continue on failure. 
                    catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE'){
                        // Copy all dependency dlls to the same dir as the EXE file 
                        powershell(returnStdout:true, script: """
                            \$dllFiles = (Get-ChildItem -Path ${WORKSPACE} -Include '*.dll' -Recurse).FullName
                            if (\$dllFiles -match "${DOTNETNUMBER}"){
                                \$dllFiles -match "${DOTNETNUMBER}" | copy-item -destination (split-path \"${EXEPATH}\".trim() -Resolve)
                            }
                            else{
                                \$dllFiles | copy-item -destination (split-path \"${EXEPATH}\".trim() -Resolve)
                            }
                        """)

                        // Generate confuserEx project file using `confuserEx.ps1` script 
                        powershell(returnStdout:true, script:"${CONFUSERPREP} -exePath \"${EXEPATH}\".trim() -outDir ${WORKSPACE}\\Confused -level normal -toolName ${TOOLNAME} ")

                        // Run confuserEx with the project file generated above
                        bat "Confuser.CLI.exe ${WORKSPACE}\\Confused\\${TOOLNAME}.crproj"

                        echo "[!] ConfuserEx failed. Skipping Obfuscation."
                    }
                }
            }
        }

        stage('Create-PSP'){
            steps{
                script{
                    def exePath = powershell(returnStdout: true, script: "(Get-ChildItem -Path ${WORKSPACE} -Include '*.exe' -Recurse | Where-Object {\$_.DirectoryName -match 'Confused'} ).FullName")
                    env.EXEPATH = exePath
                    
                    // If confuserEx failed, just use the regular bin.
                    if (env.EXEPATH == ''){
                        exePath = powershell(returnStdout: true, script: """
                            \$exeFiles = (Get-ChildItem -Path ${WORKSPACE} -Include '*.exe' -Recurse | Where-Object {\$_.DirectoryName -match 'release' -and \$_.DirectoryName -match 'bin' } ).FullName
                            if (\$exeFiles -match "${DOTNETNUMBER}"){
                                \$exeFiles -match "${DOTNETNUMBER}"
                            }
                            else{
                                (Get-ChildItem -Path ${WORKSPACE} -Include '*.exe' -Recurse | Where-Object {\$_.DirectoryName -match 'release'} )[0].FullName
                            }
                            """)
                        env.EXEPATH = exePath
                    }

                    // Beaware of environment variable created from ps in jenkins (exePath). Always .trim() INSIDE powershell.
                    powershell "${EMBEDDOTNETPATH} -inputFile \"${EXEPATH}\".trim() -outputFile ${PSP_OUTPUT} -templatePath ${TEMPLATEPATH} -toolName ${TOOLNAME}"
                }
            }
        }

        stage('Obfuscate-PSP'){
            steps{
                bat encoding: 'UTF-8', script: """python ${CHAMELEONPATH} -v -d -c -f -r -i -l 4 ${PSP_OUTPUT} -o ${OBS_PSP_OUTPUT}"""
            }
        }
    }
}