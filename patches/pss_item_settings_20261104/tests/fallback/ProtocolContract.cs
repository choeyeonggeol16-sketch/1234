using System;
using System.Net.Sockets;

// Only the test game channel is plain. Production always uses the existing game sender.
class ProtocolContract {
    public static void Main() {
        byte[] nonce = new byte[16]; for (int i=0;i<16;i++) nonce[i]=(byte)(i*17);
        using (TcpClient game = new TcpClient("127.0.0.1",5000)) {
            NetworkStream stream=game.GetStream();
            Action<int> frame=delegate(int id) { byte[] data={7,0,0x51,(byte)id,(byte)(id>>8),0,0};stream.Write(data,0,data.Length); };
            frame(0x7df0);foreach(byte value in nonce)frame(0x7e00|value);frame(0x7df1);
            using(TcpClient control=new TcpClient("127.0.0.1",5001)) {
                byte[] hello=new byte[24];System.Text.Encoding.ASCII.GetBytes("PSSUI").CopyTo(hello,0);hello[5]=1;nonce.CopyTo(hello,8);
                int bound=0,opened=0;
                PssItemProtocol.Run(control.Client,hello,delegate{return opened==0;},delegate{bound++;},delegate{if(bound!=1)throw new Exception("OPEN before BOUND");opened++;});
                if(bound!=1||opened!=1)throw new Exception("wrong window-only signal count");
                Console.WriteLine("PASS actual C# companion wire code + Java transport + window-only callback");
            }
        }
    }
}
