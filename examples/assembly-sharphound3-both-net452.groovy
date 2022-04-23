pipeline { 
    agent any
    
    environment { 
        // << CHANGE THESE >> 
        TOOLNAME = "SharpHound3"
        OBS_TOOLNAME = "SharpDoggo3"
        GITURL = "https://github.com/BloodHoundAD/SharpHound3.git"
        BRANCH = "master"
        WORKDIR = "C:\\opt\\jenkins-psp"           // git-cloned directory 
        
        PSP_OUTPUT = "${WORKDIR}\\output\\Invoke-${OBS_TOOLNAME}.ps1"
        OBS_PSP_OUTPUT = "${WORKDIR}\\output\\Obs-Invoke-${OBS_TOOLNAME}.ps1"
        ASSEMBLY_OUTPUT = "${WORKDIR}\\output\\${OBS_TOOLNAME}.exe"

        // << CHANGE THESE >> - .NET Compile configs
        CONFIG="Release"
        PLATFORM="Any CPU"
        DOTNETVERSION="v4.5.2"
        DOTNETNUMBER="net452"
        
        // 3rd party tools 
        INVISCLOAKPATH = "${WORKDIR}\\InvisibilityCloak\\InvisibilityCloak.py"
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

        // Try main, then master for old github repos.
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

        // Obfuscate with invisibilitycloak. 
        stage('InvisibilityCloak-Obfuscate') { 
            steps { 
                bat """python ${INVISCLOAKPATH} -d ${WORKSPACE} -n ${OBS_TOOLNAME} -m rot13 """
            }
        }

        // Some projects doesn't need nuget restore. Continue on failure.
        // TODO: what's this "msbuild PROJECT.sln /t:Restore /p:Configuration=Release"
        stage('Nuget-Restore'){
            steps{
                script{
                    def slnPath = powershell(returnStdout: true, script: "(Get-ChildItem -Path ${WORKSPACE} -Include '${OBS_TOOLNAME}.sln' -Recurse).FullName")
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

        // If Compilation fails due to invisiblity cloak, run without string obfuscation (delete -m rot 13)
        stage('Compile'){ 
            steps {
                script{
                    def slnPath = powershell(returnStdout: true, script: "(Get-ChildItem -Path ${WORKSPACE} -Include '${OBS_TOOLNAME}.sln' -Recurse).FullName")
                    env.SLNPATH = slnPath
                    
                    try{
                        bat "\"${tool 'MSBuild_VS2019'}\\MSBuild.exe\" /p:Configuration=${CONFIG} \"/p:Platform=${PLATFORM}\" /maxcpucount:%NUMBER_OF_PROCESSORS% /nodeReuse:false /p:DebugType=None /p:DebugSymbols=false /p:TargetFrameworkMoniker=\".NETFramework,Version=${DOTNETVERSION}\" ${SLNPATH}" 
                    }   
                    catch(Exception e){
                        catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE') {
                            bat """dotnet build --configuration ${CONFIG} ${SLNPATH} """ 
                        }
                    }  
                }
            }
        }

        // ConfuserEx only when it's not net5.0++. Only execute this stage when the dotnet version contains "v" ex. v3.5
        stage('ConfuserEx'){
            when {
                expression { env.DOTNETVERSION.contains('v')} 
            }
            steps{
                script{ 
                    // Some projects have net45/net35, some projects doesn't. So many one-offs        
                    def exePath = powershell(returnStdout: true, script: """
                    \$exeFiles = (Get-ChildItem -Path ${WORKSPACE} -Include '*.exe' -Recurse | Where-Object {\$_.DirectoryName -match 'release' -and \$_.DirectoryName -match 'bin' } ).FullName
                    if (\$exeFiles -match "${DOTNETNUMBER}"){
                        \$exeFiles.trim()
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
                        powershell(returnStdout:true, script:"${CONFUSERPREP} -exePath \"${EXEPATH}\".trim() -outDir ${WORKSPACE}\\Confused -level normal -toolName ${OBS_TOOLNAME} ")

                        // Run confuserEx with the project file generated above
                        bat "Confuser.CLI.exe ${WORKSPACE}\\Confused\\${OBS_TOOLNAME}.crproj"

                        echo "[!] ConfuserEx failed. Skipping Obfuscation."
                    }
                }
            }
        }

        stage('Move-Assembly'){
            steps{
                script{
                    def exePath = powershell(returnStdout: true, script: "(Get-ChildItem -Path ${WORKSPACE} -Include '*.exe' -Recurse | Where-Object {\$_.DirectoryName -match 'Confused'} ).FullName")
                    env.EXEPATH = exePath
                    
                    // If confuserEx failed, just use the regular bin.
                    if (env.EXEPATH == ''){
                        exePath = powershell(returnStdout: true, script: """
                            \$exeFiles = (Get-ChildItem -Path ${WORKSPACE} -Include '*.exe' -Recurse | Where-Object {\$_.DirectoryName -match 'release' -and \$_.DirectoryName -match 'bin' } ).FullName
                            if (\$exeFiles -match "${DOTNETNUMBER}"){
                                \$exeFiles.trim()
                            }
                            else{
                                (Get-ChildItem -Path ${WORKSPACE} -Include '*.exe' -Recurse | Where-Object {\$_.DirectoryName -match 'release'} )[0].FullName
                            }
                            """)
                        env.EXEPATH = exePath
                    }

                    // Beaware of environment variable created from ps in jenkins (exePath). Always .trim() INSIDE powershell.
                    powershell "mv \"${EXEPATH}\".trim() ${ASSEMBLY_OUTPUT}"
                }
            }
        }
    }
}