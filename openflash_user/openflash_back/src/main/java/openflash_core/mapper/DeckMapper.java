package openflash_core.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import openflash_core.entity.Deck;

@Mapper
public interface DeckMapper {

    List<Deck> findByUserId(Long userId);

    List<Long> findIdsByUserIdIncludingDeleted(@Param("userId") Long userId);

    Deck findById(Long id);

    Deck findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    int insert(Deck deck);

    int updateName(@Param("id") Long id, @Param("userId") Long userId, @Param("name") String name);

    int deleteById(@Param("id") Long id, @Param("userId") Long userId);
}
