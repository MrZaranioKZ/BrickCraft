from pathlib import Path
import subprocess,sys
root=Path(__file__).resolve().parent.parent
output=root/'build'/'parse_v0251';output.mkdir(parents=True,exist_ok=True)
src=root/'tools/JavaSyntaxV0251Test.java'
for cmd in (['javac','--release','21','-d',str(output),str(src)],
            ['java','-cp',str(output),'JavaSyntaxV0251Test']):
    p=subprocess.run(cmd,cwd=root)
    if p.returncode:sys.exit(p.returncode)
