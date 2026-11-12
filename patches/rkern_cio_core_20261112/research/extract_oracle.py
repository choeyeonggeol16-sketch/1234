from pathlib import Path
import openpyxl,csv,json,hashlib
r=Path(__file__).resolve().parent
w=openpyxl.load_workbook(r/'Phil_stats.xlsx',read_only=True,data_only=True)
names=['君主','ナイト','エルフ','ウィザード','ダークエルフ','ドラゴンナイト','イリュージョニスト','ウォリアー']
rows=list(w['データ'].iter_rows(min_row=1,max_row=8,min_col=16,max_col=23,values_only=True))
meta=[]
for i,name in enumerate(names):meta.append({'type':i,'name':name,'hp_cap':int(rows[1][i]),'hp_class':int(rows[2][i]),'hp_initial':int(rows[3][i]),'wis_cap':int(rows[4][i]),'mp_factor':rows[5][i],'mp_add':int(rows[6][i]),'mp_initial':int(rows[7][i])})
(r/'class-data.json').write_text(json.dumps(meta,ensure_ascii=False,indent=2)+'\n')
out=[];levels={50,51,55,60,65,70,75,80,85,88}
for kind,title,cap,initial in [('HP','HP一覧（データ元）','hp_cap','hp_initial'),('MP','MP一覧（データ元）','wis_cap','mp_initial')]:
 current=None
 for row in w[title].iter_rows(max_col=49,values_only=True):
  label=row[1]
  if label in names:current=names.index(label)
  elif label is not None and label!='クラス':current=None
  level=row[3]
  if current is None or not isinstance(level,(float,int)) or level not in levels:continue
  for stat,value in enumerate(row[4:49],1):
   if isinstance(value,(float,int)):out.append([kind,current,stat,int(level),meta[current][cap],meta[current][initial],repr(value)])
with (r/'growth-sheet-oracle.csv').open('w') as f:
 writer=csv.writer(f);writer.writerow(['kind','class','target_stat','level','creation_cap','sheet_initial','sheet_mean']);writer.writerows(out)
(r/'source.json').write_text(json.dumps({'url':'https://docs.google.com/spreadsheets/d/1P7unh7Sky0a_Iafq_9iqKBfMoUzsKDERUwSs899qpLY/edit','xlsx_sha256':hashlib.sha256((r/'Phil_stats.xlsx').read_bytes()).hexdigest(),'oracle_rows':len(out),'scope':'Cached public research-sheet means, not independent NC/2019 measurements; allocated stats increase from level 51, never start every character at stat 45.'},indent=2)+'\n')
print('Extracted',len(out),'cached sheet means and 8 class coefficient rows.')
