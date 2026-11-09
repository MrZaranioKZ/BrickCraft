import fr.brickcraft.importer.LDrawProjectReader;
import fr.brickcraft.importer.PreciseProjectIO;
import java.nio.file.Path;
public final class ConvertToBcpx {
 public static void main(String[] args)throws Exception{
  if(args.length!=2)throw new IllegalArgumentException("Usage: ConvertToBcpx <source.io|.mpd|.ldr> <destination.bcpx>");
  var model=LDrawProjectReader.inspect(Path.of(args[0]));
  PreciseProjectIO.save(Path.of(args[1]),model);
  System.out.println("BCPX: "+model.parts().size()+" occurrences, "+model.submodels()+" sections, 3D matrices conserved");
 }
}
