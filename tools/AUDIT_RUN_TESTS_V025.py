#!/usr/bin/env python3
"""Portable, offline regression runner for pure-Java BrickCraft components.
Does not validate the Fabric runtime or GPU rendering. Run from any working dir.
"""
import subprocess,sys,json,time
from pathlib import Path
ROOT=Path(__file__).resolve().parent.parent
SRC=ROOT/'src/main/java'
GROUPS={
 'guidebook_layout': (['fr/brickcraft/client/BrickGuideLayout.java','TEST_GUIDE_LAYOUT_V0231.java'],'TEST_GUIDE_LAYOUT_V0231'),
 'ui_gpu':(['fr/brickcraft/client/StudioLayout.java','fr/brickcraft/client/PreviewPerfPolicy.java','TEST_UI_GPU_014.java'],'TEST_UI_GPU_014'),
 'pbr_profiles':(['fr/brickcraft/core/BrickPbrProfiles.java','TEST_PBR_PROFILES_V022.java'],'TEST_PBR_PROFILES_V022'),
 'commands_coordinates':(['fr/brickcraft/core/BrickPalette.java','fr/brickcraft/BrickCommandPalette.java','fr/brickcraft/world/BrickCellCoordinates.java','tools/BrickCreativeCommandsV020Test.java'],'BrickCreativeCommandsV020Test'),
 'physics':(['fr/brickcraft/core/BrickCapacityConfig.java','fr/brickcraft/importer/StudioIoArchive.java','fr/brickcraft/importer/LDrawProjectReader.java','fr/brickcraft/world/PreciseMeshCollisionPlan.java','fr/brickcraft/world/ExplorationStepRules.java','TEST_PHYSIQUE_V012.java','TEST_PHYSIQUE_V013.java'],'TEST_PHYSIQUE_V013'),
 'replacements':(['fr/brickcraft/importer/StudioIoArchive.java','fr/brickcraft/importer/LDrawProjectReader.java','fr/brickcraft/importer/PreciseProjectIO.java','fr/brickcraft/importer/PartReplacementMatcher.java','fr/brickcraft/importer/PartReplacementEditor.java','TEST_EQUIVALENTS_V018.java'],'TEST_EQUIVALENTS_V018'),
 'missing_removal':(['fr/brickcraft/importer/StudioIoArchive.java','fr/brickcraft/importer/LDrawProjectReader.java','fr/brickcraft/importer/PreciseProjectIO.java','fr/brickcraft/importer/PartReplacementMatcher.java','fr/brickcraft/importer/MissingPartRemovalEditor.java','TEST_SUPPRESSION_MANQUANTES_V019.java'],'TEST_SUPPRESSION_MANQUANTES_V019'),
 'big_sets':(['fr/brickcraft/core/BrickCapacityConfig.java','fr/brickcraft/core/MaterialFinish.java','fr/brickcraft/ldraw/LDrawLibrary.java','fr/brickcraft/importer/PreciseReferenceMapper.java','fr/brickcraft/importer/StudioIoArchive.java','fr/brickcraft/importer/LDrawProjectReader.java','fr/brickcraft/importer/PreciseWorldModel.java','fr/brickcraft/importer/PreciseProjectIO.java','fr/brickcraft/importer/StudioProjectAnalysis.java','fr/brickcraft/importer/MissingPartsReport.java','fr/brickcraft/importer/StudioCustomPartsInstaller.java','fr/brickcraft/world/PreciseMeshCollisionPlan.java','TEST_GRANDS_SETS_V017.java'],'TEST_GRANDS_SETS_V017'),
 'studio_assembly':(['tools/test_stubs/net/minecraft/util/math/BlockPos.java','fr/brickcraft/core/MaterialFinish.java','fr/brickcraft/ldraw/LDrawLibrary.java','fr/brickcraft/core/BrickKind.java','fr/brickcraft/core/BrickPiece.java','fr/brickcraft/client/StudioAssembly.java','fr/brickcraft/client/StudioLayout.java','fr/brickcraft/client/StudioProjectIO.java','tools/StudioLargeWorkspaceTest.java','tools/StudioProjectIOTest.java'],'StudioLargeWorkspaceTest'),
 'studio_io':(['fr/brickcraft/core/BrickCapacityConfig.java','fr/brickcraft/core/MaterialFinish.java','fr/brickcraft/ldraw/LDrawLibrary.java','fr/brickcraft/importer/PreciseReferenceMapper.java','fr/brickcraft/importer/StudioIoArchive.java','fr/brickcraft/importer/LDrawProjectReader.java','fr/brickcraft/importer/PreciseProjectIO.java','fr/brickcraft/importer/PreciseWorldModel.java','fr/brickcraft/guide/BrickGuidePlan.java','fr/brickcraft/guide/BrickSourceStepReader.java','TEST_STUDIO_IO_V016.java'],'TEST_STUDIO_IO_V016'),
 'directories':(['fr/brickcraft/BrickDirectories.java','TEST_DIRECTORY_V024.java'],'TEST_DIRECTORY_V024'),
}
# Missing optional dependencies in some test groups are a finding, not a success.
files_external=[str(p) for p in sorted(ROOT.parent.glob('*.io'))]
which=sys.argv[1:] or list(GROUPS)
results={}
for name in which:
    sources,main=GROUPS[name]
    out=ROOT/'build'/'audit_v025'/name;out.mkdir(parents=True,exist_ok=True)
    selected=[(SRC/f if f.startswith('fr/') else ROOT/f) for f in sources]
    args=['javac','-encoding','UTF-8','-d',str(out),*map(str,selected)]
    start=time.monotonic()
    comp=subprocess.run(args,cwd=ROOT,capture_output=True,text=True)
    if comp.returncode:
        results[name]={'status':'FAIL_COMPILE','details':(comp.stderr+comp.stdout)[-2000:]}
        print('FAIL',name,'compile',results[name]['details'],flush=True);continue
    args=['java','-Xmx1024m','-cp',str(out),main]
    if name in ('big_sets','replacements','missing_removal','studio_io'):args+=files_external
    run=subprocess.run(args,cwd=ROOT,capture_output=True,text=True,timeout=120)
    results[name]={'status':'PASS' if run.returncode==0 else 'FAIL_RUNTIME',
                   'seconds':round(time.monotonic()-start,2),'details':(run.stdout+run.stderr)[-1500:]}
    print(results[name]['status'],name,results[name]['seconds'],'s',flush=True)
    if run.returncode: print(results[name]['details'],flush=True)
(ROOT/'AUDIT_TESTS_V025.json').write_text(json.dumps(results,ensure_ascii=False,indent=2),encoding='utf-8')
if any(v['status']!='PASS' for v in results.values()):sys.exit(1)
