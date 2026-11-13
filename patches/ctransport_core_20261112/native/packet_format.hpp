#pragma once
#include <cstdarg>
#include <cstdint>
#include <cstring>
#include <vector>
// Format cases recovered from0x70429c;L1 output admission remains65535 bytes.
inline bool AssemblePacket(const char* format,va_list arguments,std::vector<unsigned char>& out)noexcept{
 if(!format)return false;
 try{
  auto append=[&](const void* data,std::size_t length){if(length>65533-out.size()||(!data&&length))return false;if(length){auto* p=static_cast<const unsigned char*>(data);out.insert(out.end(),p,p+length);}return true;};
  auto integer=[&](std::uint64_t value,unsigned width){unsigned char bytes[8]{};for(unsigned n=0;n<width;++n)bytes[n]=static_cast<unsigned char>(value>>(n*8));return append(bytes,width);};
  for(auto* p=format;*p;++p)switch(*p){
   case 'c':case 'C':if(!integer(static_cast<unsigned>(va_arg(arguments,int)),1))return false;break;
   case 'h':if(!integer(static_cast<unsigned>(va_arg(arguments,int)),2))return false;break;
   case 'd':if(!integer(static_cast<unsigned>(va_arg(arguments,int)),4))return false;break;
   case 'Q':if(!integer(va_arg(arguments,std::uint64_t),8))return false;break;
   case 'p':if(!integer(reinterpret_cast<std::uintptr_t>(va_arg(arguments,void*)),8))return false;break;
   case 'f':{double value=va_arg(arguments,double);std::uint64_t bits;std::memcpy(&bits,&value,8);if(!integer(bits,8))return false;break;}
   case 'b':{int size=va_arg(arguments,int);auto* bytes=va_arg(arguments,const void*);if(size<0||!append(bytes,static_cast<unsigned>(size)))return false;break;}
   case 's':{auto* text=va_arg(arguments,const char*);if(text){for(std::size_t n=0;;++n){if(!append(text+n,1))return false;if(!text[n])break;}}else if(!integer(0,1))return false;break;}
   case 'S':{auto* text=va_arg(arguments,const char16_t*);if(text){for(std::size_t n=0;;++n){if(!integer(text[n],2))return false;if(!text[n])break;}}else if(!integer(0,2))return false;break;}
   default:break; //Original ignores unknown format bytes without consuming args.
  }
  return true;
 }catch(...){return false;}
}
