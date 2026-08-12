package com.smartsql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartsql.entity.Message;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MessageMapper extends BaseMapper<Message> {
}
