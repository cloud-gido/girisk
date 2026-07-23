package com.girisk.auth;

import com.girisk.auth.dto.CreateUserRequest;
import com.girisk.auth.dto.IamRoleView;
import com.girisk.auth.dto.IamUserView;
import com.girisk.auth.dto.ResetPasswordRequest;
import com.girisk.auth.dto.UpdateRolePermissionsRequest;
import com.girisk.auth.dto.UpdateUserRequest;
import com.girisk.auth.model.SysPermission;
import com.girisk.auth.model.SysRole;
import com.girisk.auth.model.SysUser;
import com.girisk.auth.rbac.RbacPermissions;
import com.girisk.auth.rbac.RbacService;
import com.girisk.auth.repository.RbacRepository;
import com.girisk.auth.repository.UserRepository;
import com.girisk.common.exception.BusinessException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class IamService {

    private final UserRepository userRepository;
    private final RbacRepository rbacRepository;
    private final RbacService rbacService;
    private final PasswordEncoder passwordEncoder;

    public IamService(
            UserRepository userRepository,
            RbacRepository rbacRepository,
            RbacService rbacService,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.rbacRepository = rbacRepository;
        this.rbacService = rbacService;
        this.passwordEncoder = passwordEncoder;
    }

    public List<IamUserView> listUsers() {
        return userRepository.listAll().stream().map(this::toUserView).toList();
    }

    public IamUserView getUser(long id) {
        SysUser user = userRepository.findById(id).orElseThrow(() -> new BusinessException("用户不存在"));
        return toUserView(user);
    }

    @Transactional
    public IamUserView createUser(CreateUserRequest req) {
        if (userRepository.findByUsername(req.username()).isPresent()) {
            throw new BusinessException("用户名已存在");
        }
        String primary = normalizeRole(req.role());
        List<String> roles = resolveRoleCodes(primary, req.roles());
        long id = userRepository.insert(
                req.username().trim(),
                passwordEncoder.encode(req.password()),
                req.displayName().trim(),
                primary);
        bindRoles(id, roles);
        return getUser(id);
    }

    @Transactional
    public IamUserView updateUser(long id, UpdateUserRequest req) {
        SysUser user = userRepository.findById(id).orElseThrow(() -> new BusinessException("用户不存在"));
        String primary = normalizeRole(req.role());
        boolean enabled = req.enabled() == null ? user.enabled() : req.enabled();
        userRepository.updateProfile(id, req.displayName().trim(), primary, enabled);
        List<String> roles = resolveRoleCodes(primary, req.roles());
        bindRoles(id, roles);
        return getUser(id);
    }

    public IamUserView setEnabled(long id, boolean enabled) {
        if (userRepository.findById(id).isEmpty()) {
            throw new BusinessException("用户不存在");
        }
        userRepository.setEnabled(id, enabled);
        return getUser(id);
    }

    public void resetPassword(long id, ResetPasswordRequest req) {
        if (userRepository.findById(id).isEmpty()) {
            throw new BusinessException("用户不存在");
        }
        userRepository.updatePassword(id, passwordEncoder.encode(req.password()));
    }

    public List<IamRoleView> listRoles() {
        return rbacRepository.listRoles().stream().map(this::toRoleView).toList();
    }

    public List<SysPermission> listPermissions() {
        return rbacRepository.listPermissions();
    }

    @Transactional
    public IamRoleView updateRolePermissions(long roleId, UpdateRolePermissionsRequest req) {
        SysRole role = rbacRepository.findRoleById(roleId).orElseThrow(() -> new BusinessException("角色不存在"));
        if (role.builtin() && RbacPermissions.ROLE_ADMIN.equals(role.code())) {
            // ADMIN 始终全权限，忽略缩减
            bindRoleAllPermissions(role.id());
            return toRoleView(role);
        }
        List<Long> permIds = new ArrayList<>();
        for (String code : req.permissions()) {
            rbacRepository.findPermissionByCode(code)
                    .ifPresentOrElse(p -> permIds.add(p.id()), () -> {
                        throw new BusinessException("未知权限: " + code);
                    });
        }
        rbacRepository.replaceRolePermissions(role.id(), permIds);
        return toRoleView(role);
    }

    private void bindRoleAllPermissions(long roleId) {
        List<Long> ids = rbacRepository.listPermissions().stream().map(SysPermission::id).toList();
        rbacRepository.replaceRolePermissions(roleId, ids);
    }

    private void bindRoles(long userId, List<String> roleCodes) {
        List<Long> roleIds = new ArrayList<>();
        for (String code : roleCodes) {
            SysRole role = rbacRepository.findRoleByCode(code)
                    .orElseThrow(() -> new BusinessException("未知角色: " + code));
            roleIds.add(role.id());
        }
        if (roleIds.isEmpty()) {
            throw new BusinessException("至少绑定一个角色");
        }
        rbacRepository.replaceUserRoles(userId, roleIds);
    }

    private List<String> resolveRoleCodes(String primary, List<String> roles) {
        if (roles != null && !roles.isEmpty()) {
            return roles.stream().map(this::normalizeRole).distinct().toList();
        }
        return List.of(primary);
    }

    private String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            throw new BusinessException("角色不能为空");
        }
        String code = role.trim().toUpperCase();
        if (rbacRepository.findRoleByCode(code).isEmpty()) {
            throw new BusinessException("未知角色: " + code);
        }
        return code;
    }

    private IamUserView toUserView(SysUser user) {
        List<String> roles = rbacService.rolesForUser(user);
        List<String> perms = rbacService.permissionsForUser(user);
        String primary = roles.isEmpty() ? user.role() : roles.get(0);
        return new IamUserView(
                user.id(),
                user.username(),
                user.displayName(),
                primary,
                user.enabled(),
                roles,
                perms,
                user.createdAt() == null ? null : user.createdAt().toString());
    }

    private IamRoleView toRoleView(SysRole role) {
        return new IamRoleView(
                role.id(),
                role.code(),
                role.name(),
                role.builtin(),
                role.description(),
                rbacRepository.findPermissionCodesByRoleId(role.id()));
    }
}
