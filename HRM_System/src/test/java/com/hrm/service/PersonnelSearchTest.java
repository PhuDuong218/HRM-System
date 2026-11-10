package com.hrm.service;
import com.hrm.util.PersonnelSearch;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
class PersonnelSearchTest {
 @Test void vietnameseSearchHandlesMarksCaseAndMultipleWords(){assertTrue(PersonnelSearch.matches("  han  ","Lê Ngọc Hân"));assertTrue(PersonnelSearch.matches("DANG han","Đặng Ngọc Hân"));assertTrue(PersonnelSearch.matches("han", "Ha\u0302n"));assertFalse(PersonnelSearch.matches("han mai","Lê Ngọc Hân"));}
 @Test void symbolsAreLiteralAndMissingValuesAreSafe(){assertFalse(PersonnelSearch.matches("%","Hân"));assertTrue(PersonnelSearch.matches("",(Object)null));assertFalse(PersonnelSearch.matches("_","Hân"));}
 @Test void filteringMustPrecedePaginationAndCountsRemainCorrect(){var matches=List.of("Mai","Hân","Han","Hoa","HÂN").stream().filter(s->PersonnelSearch.matches("han",s)).toList();var page=PersonnelSearch.page(matches,1,2);assertEquals(3,page.getTotalElements());assertEquals(2,page.getTotalPages());assertEquals(List.of("HÂN"),page.getContent());assertTrue(PersonnelSearch.page(matches,999,2).isEmpty());}
 @Test void invalidPagingIsRejected(){assertThrows(org.springframework.web.server.ResponseStatusException.class,()->PersonnelSearch.page(List.of(),-1,10));assertThrows(org.springframework.web.server.ResponseStatusException.class,()->PersonnelSearch.page(List.of(),0,101));}
}
