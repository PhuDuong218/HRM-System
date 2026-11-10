package com.hrm.util;
import java.text.Normalizer;
import java.util.*;
import org.springframework.data.domain.*;
public final class PersonnelSearch {
 private PersonnelSearch(){}
 public static String normalize(String text){return Normalizer.normalize(Objects.toString(text,""),Normalizer.Form.NFD).replaceAll("\\p{M}+", "").replace('đ','d').replace('Đ','D').toLowerCase(Locale.ROOT).trim();}
 public static boolean matches(String keyword,Object... fields){String text=normalize(Arrays.stream(fields).map(x->Objects.toString(x,"")).collect(java.util.stream.Collectors.joining(" ")));return Arrays.stream(normalize(keyword).replaceAll("[—–]", " ").replaceAll("(?<!\\S)-(?!\\S)", " ").trim().split("\\s+")).allMatch(text::contains);}
 public static <T> Page<T> page(List<T> rows,int page,int size){var paging=PagingUtils.create(page,size,Sort.unsorted());long offset=paging.getOffset();int from=(int)Math.min(offset,rows.size());return new PageImpl<>(rows.subList(from,Math.min(from+size,rows.size())),paging,rows.size());}
}
