package ai.chat2db.server.domain.core.impl;

import java.util.List;
import java.util.Objects;

import ai.chat2db.server.domain.api.enums.RoleCodeEnum;
import ai.chat2db.server.domain.api.model.User;
import ai.chat2db.server.domain.api.param.user.UserCreateParam;
import ai.chat2db.server.domain.api.param.user.UserPageQueryParam;
import ai.chat2db.server.domain.api.param.user.UserSelector;
import ai.chat2db.server.domain.api.param.user.UserUpdateParam;
import ai.chat2db.server.domain.api.service.UserService;
import ai.chat2db.server.domain.core.converter.UserConverter;
import ai.chat2db.server.domain.repository.entity.DbhubUserDO;
import ai.chat2db.server.domain.repository.mapper.DbhubUserMapper;
import ai.chat2db.server.tools.base.excption.BusinessException;
import ai.chat2db.server.tools.base.wrapper.result.ActionResult;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.tools.base.wrapper.result.ListResult;
import ai.chat2db.server.tools.base.wrapper.result.PageResult;
import ai.chat2db.server.tools.common.exception.DataAlreadyExistsBusinessException;
import ai.chat2db.server.tools.common.exception.ParamBusinessException;
import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/**
 * 用户服务
 *
 * @author 是仪
 */
@Service
public class UserServiceImpl implements UserService {

    @Resource
    private DbhubUserMapper dbhubUserMapper;
    @Resource
    private UserConverter userConverter;

    @Override
    public DataResult<User> query(Long id) {
        return DataResult.of(userConverter.do2dto(dbhubUserMapper.selectById(id)));
    }

    @Override
    public DataResult<User> query(String userName) {
        LambdaQueryWrapper<DbhubUserDO> query = new LambdaQueryWrapper<>();
        if (Objects.nonNull(userName)) {
            query.eq(DbhubUserDO::getUserName, userName);
        }
        DbhubUserDO dbhubUserDO = dbhubUserMapper.selectOne(query);
        return DataResult.of(userConverter.do2dto(dbhubUserDO));
    }

    @Override
    public ListResult<User> listQuery(List<Long> idList) {
        if (CollectionUtils.isEmpty(idList)) {
            return ListResult.empty();
        }
        LambdaQueryWrapper<DbhubUserDO> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.in(DbhubUserDO::getId, idList);
        List<DbhubUserDO> dataList = dbhubUserMapper.selectList(queryWrapper);
        List<User> list = userConverter.do2dto(dataList);
        return ListResult.of(list);
    }

    @Override
    public PageResult<User> pageQuery(UserPageQueryParam param, UserSelector selector) {
        LambdaQueryWrapper<DbhubUserDO> queryWrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(param.getSearchKey())) {
            queryWrapper.and(wrapper -> wrapper.like(DbhubUserDO::getUserName, "%" + param.getSearchKey() + "%")
                .or()
                .like(DbhubUserDO::getNickName, "%" + param.getSearchKey() + "%")
                .or()
                .like(DbhubUserDO::getEmail, "%" + param.getSearchKey() + "%"));
        }
        // Default not to query desktop accounts
        queryWrapper.ne(DbhubUserDO::getId, RoleCodeEnum.DESKTOP.getDefaultUserId());
        Page<DbhubUserDO> page = new Page<>(param.getPageNo(), param.getPageSize());
        page.setSearchCount(param.getEnableReturnCount());
        IPage<DbhubUserDO> iPage = dbhubUserMapper.selectPage(page, queryWrapper);
        return PageResult.of(userConverter.do2dto(iPage.getRecords()), iPage.getTotal(), param);
    }

    @Override
    public DataResult<Long> update(UserUpdateParam param) {
        if (RoleCodeEnum.DESKTOP.getDefaultUserId().equals(param.getId())) {
            throw new BusinessException("user.canNotOperateSystemAccount");
        }
        if (RoleCodeEnum.DESKTOP.getCode().equals(param.getRoleCode())) {
            throw new ParamBusinessException("roleCode");
        }

        DbhubUserDO data = userConverter.param2do(param);
        if (Objects.nonNull(data.getPassword())) {
            String bcryptPassword = DigestUtil.bcrypt(data.getPassword());
            data.setPassword(bcryptPassword);
        }

        if (RoleCodeEnum.ADMIN.getDefaultUserId().equals(param.getId())) {
            data.setStatus(null);
            data.setEmail(null);
            data.setUserName(null);
            data.setRoleCode(null);
        }
        dbhubUserMapper.updateById(data);
        return DataResult.of(data.getId());
    }

    @Override
    public ActionResult delete(Long id) {
        if (RoleCodeEnum.DESKTOP.getDefaultUserId().equals(id) || RoleCodeEnum.ADMIN.getDefaultUserId().equals(id)) {
            throw new BusinessException("user.canNotOperateSystemAccount");
        }
        dbhubUserMapper.deleteById(id);
        return ActionResult.isSuccess();
    }

    @Override
    public DataResult<Long> create(UserCreateParam param) {
        LambdaQueryWrapper<DbhubUserDO> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.and(wrapper -> wrapper.eq(DbhubUserDO::getUserName, param.getUserName())
            .or()
            .eq(DbhubUserDO::getEmail, param.getEmail()));
        Page<DbhubUserDO> page = new Page<>(1, 1);
        page.setSearchCount(false);
        IPage<DbhubUserDO> iPage = dbhubUserMapper.selectPage(page, queryWrapper);
        if (CollectionUtils.isNotEmpty(iPage.getRecords())) {
            throw new DataAlreadyExistsBusinessException("userName or email",
                param.getUserName() + " or " + param.getEmail());
        }
        if (RoleCodeEnum.DESKTOP.getCode().equals(param.getRoleCode())) {
            throw new ParamBusinessException("roleCode");
        }

        DbhubUserDO data = userConverter.param2do(param);
        String bcryptPassword = DigestUtil.bcrypt(data.getPassword());
        data.setPassword(bcryptPassword);
        dbhubUserMapper.insert(data);
        return DataResult.of(data.getId());
    }
}
