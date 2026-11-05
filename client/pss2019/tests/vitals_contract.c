#define PSS_VITALS_TEST
#include <assert.h>
#include <stdio.h>
#include "../PssVitals.c"
typedef struct {uint32_t address;void* data;SIZE_T size;} Region;
static Region regions[16];static unsigned count;
static uint32_t phase,root,hp_desc[3],mp_desc[3],hp_table[4],mp_table[4];
static BYTE root_data[128],stats[128],root_code[12],hp_code[12],mp_code[12],hp_condition[104],mp_condition[104];
static BOOL racing;static unsigned descriptor_reads;
void PssBridgeLog(const wchar_t* text){(void)text;}
BOOL PssVitalsTestRead(uint32_t address,void* out,SIZE_T size){
 if(address==0x16E2FB0&&racing&&++descriptor_reads==2)hp_desc[2]^=1;
 for(unsigned n=0;n<count;++n){Region* r=&regions[n];
  if(address>=r->address&&(uint64_t)address+size<=(uint64_t)r->address+r->size){memcpy(out,(BYTE*)r->data+address-r->address,size);return TRUE;}
 }
 return FALSE;
}
static void add(uint32_t a,void* data,SIZE_T size){regions[count++]=(Region){a,data,size};}
static void setup(void){
 count=0;phase=3;root=0x20000000;racing=FALSE;descriptor_reads=0;PssVitalsReset();
 memset(root_data,0,sizeof(root_data));memset(stats,0,sizeof(stats));
 uint32_t stat_address=0x20001000;memcpy(root_data+0x24,&stat_address,4);
 memcpy(root_code,(BYTE[]){0x8B,0x41,0x24,0xC3,0,0,0,0,0,0,0,0},12);
 memcpy(hp_code,(BYTE[]){0x66,0x8B,0x41,0x10,0xC3,0,0,0,0,0,0,0},12);
 memcpy(mp_code,(BYTE[]){0x0F,0xB7,0x41,0x14,0xC3,0,0,0,0,0,0,0},12);
 uint16_t max_hp=1250,max_mp=120;memcpy(stats+0x10,&max_hp,2);memcpy(stats+0x14,&max_mp,2);
 hp_desc[0]=0xC0017921U^2;hp_desc[1]=0x20002000;hp_desc[2]=0xA36C21F0;hp_table[2]=743^hp_desc[2];
 mp_desc[0]=0xC0017921U^1;mp_desc[1]=0x20003000;mp_desc[2]=0x54A72D19;mp_table[1]=41^mp_desc[2];
 memcpy(hp_condition,hp_signature,sizeof(hp_condition));memcpy(mp_condition,mp_signature,sizeof(mp_condition));
 add(0x15D8E44,&phase,4);add(0x15B3D94,&root,4);add(root,root_data,sizeof(root_data));add(stat_address,stats,sizeof(stats));
 add(0xEB19E0,root_code,12);add(0xEB1A00,hp_code,12);add(0xEB1A20,mp_code,12);
 add(0x16E2FB0,hp_desc,12);add(0x16E2FBC,mp_desc,12);add(hp_desc[1],hp_table,sizeof(hp_table));add(mp_desc[1],mp_table,sizeof(mp_table));
 add(0x679120,hp_condition,sizeof(hp_condition));add(0x679940,mp_condition,sizeof(mp_condition));
}
int main(void){
 PssVitals v;setup();assert(PssVitalsRead(&v));assert(v.hp==743&&v.max_hp==1250&&v.mp==41&&v.max_mp==120);
 hp_table[2]=0^hp_desc[2];assert(PssVitalsRead(&v)&&v.hp==0);
 hp_table[2]=1100^hp_desc[2];mp_table[1]=90^mp_desc[2];assert(PssVitalsRead(&v)&&v.hp==1100&&v.mp==90);
 phase=0;assert(!PssVitalsRead(&v)&&!v.hp&&!v.max_hp);phase=3;
 root=0;assert(!PssVitalsRead(&v));
 setup();root_code[0]=0xE8;assert(!PssVitalsRead(&v)); // never execute a complex getter
 setup();root_code[3]=0xC2;assert(!PssVitalsRead(&v));
 setup();hp_condition[3]^=1;assert(!PssVitalsRead(&v));
 setup();mp_condition[8]^=1;assert(!PssVitalsRead(&v));
 setup();racing=TRUE;assert(!PssVitalsRead(&v));
 setup();hp_desc[1]=0xFFFFFFFC;assert(!PssVitalsRead(&v));
 setup();uint16_t zero=0;memcpy(stats+0x14,&zero,2);mp_table[1]=mp_desc[2];assert(PssVitalsRead(&v)&&!v.max_mp&&!v.mp);
 memcpy(stats+0x10,&zero,2);assert(!PssVitalsRead(&v));
 setup();memcpy(root_code,(BYTE[]){0x8D,0x41,0x24,0xC3},4);uint32_t address;
 assert(simple_getter(0xEB19E0,root,&address)&&address==root+0x24);
 assert(!simple_getter(0xEB19E0,0xFFFFFFF0,&address));
 puts("PASS read-only native HP/MP adapter: exact signatures, indirect/XOR values, word/zero-extended getters, death, changes, max MP zero, world exit, invalid/null pointers, descriptor races, overflow and refusing complex native code");
 puts("Getter instruction shapes are checked live; actual client values still require comparison in game.");
}
