using System;
using System.IO;
class PatchContract {
    static int checks;
    static void Check(bool value,string why){checks++;if(!value)throw new Exception(why);}
    static void Refused(Action action,string why){try{action();}catch(InvalidOperationException){checks++;return;}throw new Exception(why);}
    public static void Main(string[] args){
        byte[] original=File.ReadAllBytes(args[0]);string root=Path.GetFullPath(args[1]);Directory.CreateDirectory(root);
        byte[] patched=PssClientPatch.Build(original);Check(PssClientPatch.Hash(original)==PssClientPatch.OriginalHash,"input mutated");
        Check(patched.Length==original.Length+4096,"unexpected growth");
        File.WriteAllBytes(Path.Combine(root,"patched-analysis.bin"),patched);
        byte[] wrong=(byte[])original.Clone();wrong[4096]^=1;Refused(delegate{PssClientPatch.Build(wrong);},"wrong version accepted");
        string folder=Path.Combine(root,"install-contract");Directory.CreateDirectory(folder);string game=Path.Combine(folder,"game.bin");
        string dll=Path.Combine(folder,"Pss2019.dll");File.Copy(args[2],dll,true);File.Copy(args[2]+".sha256",dll+".sha256",true);
        File.WriteAllBytes(game,original);Check(PssClientPatch.Find(folder)==game,"automatic discovery");
        PssClientPatch.Apply(game);Check(PssClientPatch.Hash(File.ReadAllBytes(game))==PssClientPatch.Hash(patched),"apply differs");
        Check(PssClientPatch.Hash(File.ReadAllBytes(game+".pss-original.bak"))==PssClientPatch.OriginalHash,"backup differs");
        PssClientPatch.Apply(game);Check(PssClientPatch.Find(folder)==game,"patched rediscovery");
        PssClientPatch.Restore(game);Check(PssClientPatch.Hash(File.ReadAllBytes(game))==PssClientPatch.OriginalHash,"restore differs");
        PssClientPatch.Restore(game);Check(PssClientPatch.Hash(File.ReadAllBytes(game))==PssClientPatch.OriginalHash,"restore not idempotent");
        File.WriteAllBytes(dll,new byte[]{1,2,3});Refused(delegate{PssClientPatch.Apply(game);},"wrong DLL accepted");
        Check(PssClientPatch.Hash(File.ReadAllBytes(game))==PssClientPatch.OriginalHash,"bad DLL modified client");
        File.Copy(args[2],dll,true);File.WriteAllBytes(game+".pss-original.bak",wrong);
        Refused(delegate{PssClientPatch.Apply(game);},"bad backup overwritten");
        File.WriteAllBytes(game+".pss-original.bak",original);File.WriteAllBytes(game,wrong);
        Refused(delegate{PssClientPatch.Restore(game);},"later unrelated modification overwritten");
        Console.WriteLine("PASS actual C# patch/install/restore contract checks="+checks);
    }
}
