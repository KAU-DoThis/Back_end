package com.dothis.fintris.auth.repository;

import com.dothis.fintris.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    // 삭제된 행 수로 사용 여부를 판단한다. 같은 토큰으로 동시에 재발급해도 한 요청만 성공한다
    @Modifying
    @Query("delete from RefreshToken r where r.token = :token")
    int deleteByToken(@Param("token") String token);
}
