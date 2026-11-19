package l1j.server.server.datatables;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import l1j.server.MJTemplate.Command.MJCommand;
import l1j.server.MJTemplate.Command.MJCommandArgs;
import l1j.server.MJTemplate.Command.MJCommandTree;

public class MJAlchemyProbabilityBox implements MJCommand{
	private static MJAlchemyProbabilityBox _instance;
	public static MJAlchemyProbabilityBox getInstance(){
		if(_instance == null)
			_instance = new MJAlchemyProbabilityBox();
		return _instance;
	}

	public static void release(){
		if(_instance != null){
			_instance.dispose();
			_instance = null;
		}
	}

	public static void reload(){
		MJAlchemyProbabilityBox old = _instance;
		_instance = new MJAlchemyProbabilityBox();
		if(old != null){
			old.dispose();
			old = null;
		}
	}

	private ArrayList<ArrayList<Integer>> m_probability_boxes;
	private int[] m_boxes_index;
	private MJCommandTree _commands;
	private MJAlchemyProbabilityBox(){
		initialize();
	}
	
	public void shuffleList(int alchemyId){
		ArrayList<Integer> list = m_probability_boxes.get(alchemyId - 1);
		ArrayList<Integer> new_list = new ArrayList<Integer>(list);
		Collections.shuffle(new_list);
		m_probability_boxes.set(alchemyId - 1, new_list);
	}
	
	public void shuffleList(){
		try{
			for(int i=1; i<=6; ++i)
				shuffleList(i);
		}catch(Exception e){
			e.printStackTrace();
		}
	}
	
	public void initialize_index(int alchemyId){
		m_boxes_index[alchemyId - 1] = 0;
	}
	
	public void initialize_index(){
		for(int i=1; i<=6; ++i)
			initialize_index(i);		
	}
	
	// 그렇게 예민하게 동기화 될 필요가 없으므로 별도로 동기화 하지 않음.
	// 중복이 나와도 무난한 시스템.
	public int nextCurrentAlchemyId(int alchemyId){
		int index = m_boxes_index[alchemyId - 1];
		ArrayList<Integer> list = m_probability_boxes.get(alchemyId - 1);
		int result = list.get(index % list.size());
		m_boxes_index[alchemyId - 1] = index >= list.size() ? 0 : index + 1;
		return result;
	}
	
	private void initialize(){
		_commands = createCommand();
		m_probability_boxes = new ArrayList<ArrayList<Integer>>(6);
		m_boxes_index = new int[]{0,0,0,0,0,0,0,0};
		for(int i=1; i<=8; ++i)
		{
			try{
				ArrayList<Integer> list = loadProbabilityBox(i);
				if(list != null)
					m_probability_boxes.add(list);
			}catch(Exception e){
				e.printStackTrace();
			}
		}
	}
	
	private ArrayList<Integer> loadProbabilityBox(int alchemyLevel) throws UnsupportedEncodingException{
		String path = String.format("config/AlchemyBox%d.txt", alchemyLevel);
		byte[] buff = readFile(path);
		if(buff == null){
			System.out.println(String.format("%s가 없거나 읽는데 문제가 발생했습니다.", path));
			return null;
		}
		
		String boxesMessage = new String(buff, "MS949");
		String[] boxesInfo = boxesMessage.split("\r\n");
		int count = boxesInfo.length;
		ArrayList<Integer> list = new ArrayList<Integer>(count);
		for(int i=0; i<count; ++i){
			String info = boxesInfo[i]
					.trim()
					.replace(",", "");
			
			if(isNullOrEmpty(info))
				continue;
			try{
				list.add(Integer.parseInt(info));
			}catch(Exception e){
				System.out.println(String.format("%s에서 읽을 수 없는 데이터가 발견되어 패스합니다. => %s", path, info));
				e.printStackTrace();
			}
		}
		if(list == null || list.size() <= 0)
			return null;
		
		return list;
	}
	
	private static boolean isNullOrEmpty(String s){
		return s == null || s.equals("");
	}
	
	private byte[] readFile(String path){
		FileInputStream 	fs = null;
		BufferedInputStream is = null;
		byte[] 				buff = null;
		try{
			fs 			= new FileInputStream(path);
			is 			= new BufferedInputStream(fs);
			buff = new byte[(int)fs.getChannel().size()];
			is.read(buff, 0, buff.length);
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			if(fs != null){
				try{
					fs.close();
					fs = null;
				}catch(Exception e){}
			}
			
			if(is != null){
				try{
					is.close();
					is = null;
				}catch(Exception e){}
			}
		}
		return buff;
	}

	public void dispose(){
		if(m_probability_boxes != null){
			m_probability_boxes = null;
		}
	}

	@Override
	public void execute(MJCommandArgs args) {
		_commands.execute(args, new StringBuilder(256).append(_commands.to_operation()));
	}
	
	private MJCommandTree createCommand(){
		return new MJCommandTree(".인형박스", "[리로드][인덱스초기화][섞기]", null)
				.add_command(new MJCommandTree("리로드", "", null){
					@Override
					protected void to_handle_command(MJCommandArgs args) throws Exception{
						reload();
						args.notify("인형 확률 박스를 리로드 하였습니다.");
					}
				})
				.add_command(new MJCommandTree("인덱스초기화", "", null){
					@Override
					protected void to_handle_command(MJCommandArgs args) throws Exception{
						initialize_index();
						args.notify("인형 확률 박스 인덱스를 초기화했습니다.");
					}
				})
				.add_command(new MJCommandTree("섞기", "", null){
					@Override
					protected void to_handle_command(MJCommandArgs args) throws Exception{
						shuffleList();
						args.notify("인형 확률 박스를 섞었습니다.");
					}
				});
		
	}
}
