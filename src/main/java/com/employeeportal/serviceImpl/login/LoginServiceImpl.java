package com.employeeportal.serviceImpl.login;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

import com.employeeportal.exception.NotFoundException;
import com.employeeportal.model.*;
import com.employeeportal.model.onboarding.EmployeeOrganizationDetails;
import com.employeeportal.model.onboarding.Role;
import com.employeeportal.model.registration.Employee;
import com.employeeportal.model.registration.EmployeeReg;
import com.employeeportal.repository.*;
import com.employeeportal.repository.onboarding.EmployeeOrganizationDetailsRepository;
import com.employeeportal.repository.onboarding.RoleRepository;
import com.employeeportal.repository.registration.EmployeeRegRepository;
import com.employeeportal.repository.registration.EmployeeRepository;
import com.employeeportal.util.JwtUtil;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.employeeportal.config.ApplicationConstant;
import com.employeeportal.config.EmailConstant;
import com.employeeportal.service.EmailService;
import com.employeeportal.service.login.LoginService;

@Service
public class LoginServiceImpl implements LoginService {

    private final EmployeeRepository employeeRepository;

    @Autowired
    private final JwtRepository jwtRepository;
    private final JwtUtil jwtUtil;

    private final EmailService emailService;
    // private final EmployeeRelativeRepository employeeRelativeRepository;
    // private final PersonalDetailsRepository personalDetailsRepository;
    // private final PermanentAddressRepository permanentAddressRepository;
    // private final CurrentAddressRepository currentAddressRepository;
    // private final AddressDetailsRepository addressDetailsRepository;
    private final PasswordEncoder passwordEncoder;
    // private final BCryptPasswordEncoder passwordEncoder = new
    // BCryptPasswordEncoder();
    private final RedisTemplate<String, Object> redisTemplate;
    // private final DocumentCertificatesRepository documentCertificatesRepository;
    // private final EducationalQualificationRepository educationalQualificationRepository;
    // private final ProfessionalReferencesRepository professionalReferencesRepository;
    // private final EmploymentHistoryRepository employmentHistoryRepository;
    // private final PassportDetailsRepository passportDetailsRepository;
    // private final VisaStatusRepository visaStatusRepository;
    // private final WorkPermitRepository workPermitRepository;
    // private final OtherDetailsRepository otherDetailsRepository;
    // private final RelativeInfoRepository relativeInfoRepository;
    // private final PreviewRepository previewRepository;

    @Autowired
    private EmployeeRegRepository employeeRegRepository;

    @Autowired
    private EmployeeOrganizationDetailsRepository employeeOrganizationDetailsRepository;

    @Autowired
    private RoleRepository roleRepository;

    private static final long OTP_EXPIRATION_TIME = 2; // 2 minutes

    @Autowired
    public LoginServiceImpl(EmployeeRepository employeeRepository, JwtRepository jwtRepository, JwtUtil jwtUtil, EmailService emailService, PasswordEncoder passwordEncoder, RedisTemplate<String, Object> redisTemplate) {
        this.employeeRepository = employeeRepository;
        this.jwtRepository = jwtRepository;
        this.jwtUtil = jwtUtil;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public LoginResponse verifyLogin(LoginRequest loginRequest) throws Exception {
        // Find the employee by email using EmployeeRegRepository
        EmployeeReg employeeReg = employeeRegRepository.findByEmail(loginRequest.getEmail());

        if (employeeReg == null) {
            throw new BadCredentialsException(ApplicationConstant.AUTHORIZATION_EMAIL_ERROR);
        }
        System.out.println(employeeReg.getPassword()+" "+loginRequest.getPassword());
        // Check if the email matches and validate the password

        System.out.println("111");
        if (!passwordEncoder.matches(loginRequest.getPassword(), employeeReg.getPassword())) {
            System.out.println("sjsksksl");
            throw new BadCredentialsException(ApplicationConstant.AUTHORIZATION_PASSWORD_ERROR);
        }

        System.out.println("2222");
        // Prepare LoginResponse
        LoginResponse loginResponse = new LoginResponse();
        loginResponse.setEmail(employeeReg.getEmployee().getEmail());

        // Fetch full name from Employee table
        Employee employee = employeeRepository.findByEmployeeId(employeeReg.getEmployee().getEmployeeId());
        String fullName = employee.getFirstName() + " " + employee.getMiddleName() + " " + employee.getLastName();
        loginResponse.setFullName(fullName);

        // Fetch role name from Role table using role ID from
        // EmployeeOrganizationDetails
        EmployeeOrganizationDetails orgDetails = employeeOrganizationDetailsRepository
                .findByEmployeeId(employeeReg.getEmployee().getEmployeeId());
        Role role = roleRepository.findById(orgDetails.getRole().getRoleId()).get();
        loginResponse.setRoleName(role.getRoleName());

        // Generate JWT token
        loginResponse.setToken(jwtUtil.generateToken(loginRequest.getEmail()));

        return loginResponse;
    }

    @Override
    public void sendPasswordResetEmail(String email) throws Exception {
        Employee employee = employeeRepository.findByEmail(email);
        if (employee == null) {
            throw new Exception("employee not found");
        }

        String token = UUID.randomUUID().toString();
        jwtRepository.save(new JwtEntity(token, true, employee.getEmployeeId()));

        emailService.sendEmail(email, token, "Password Reset Request", "passwordResetTemplate");
    }

    @Override
    public void resetPassword(String token, String newPassword) throws Exception {

        JwtEntity jwtEntity = jwtRepository.findByJtiAndValidSession(token, true);
        if (jwtEntity == null) {
            throw new Exception("Invalid token or employee not found");
        }

        EmployeeReg employeeReg = employeeRegRepository.findByEmployeeId(jwtEntity.getPrimaryId());
        employeeReg.setPassword(passwordEncoder.encode(newPassword));
        employeeRegRepository.save(employeeReg);
        System.out.println("Password updated successfully for employee: " + employeeReg.getEmail());
    }

    public void logout(String token) {

        Claims claims = jwtUtil.getClaims(token);
        JwtEntity jwtEntity = jwtRepository.findByJtiAndValidSession(claims.getId(), true);
        if (jwtEntity != null) {
            jwtEntity.setValidSession(false);
            jwtRepository.saveAndFlush(jwtEntity);
        }
    }

}
