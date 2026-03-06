import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { FormsModule } from '@angular/forms';
import { of, throwError } from 'rxjs';
import { RegisterComponent } from './register.component';
import { AuthService } from '../../services/auth.service';

describe('RegisterComponent', () => {
  let component: RegisterComponent;
  let fixture: ComponentFixture<RegisterComponent>;
  let authServiceSpy: jasmine.SpyObj<AuthService>;

  beforeEach(async () => {
    authServiceSpy = jasmine.createSpyObj('AuthService', ['register']);

    await TestBed.configureTestingModule({
      imports: [RegisterComponent, HttpClientTestingModule, RouterTestingModule, FormsModule],
      providers: [
        { provide: AuthService, useValue: authServiceSpy }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(RegisterComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should initialize with empty fields', () => {
    expect(component.registerData.username).toBe('');
    expect(component.registerData.email).toBe('');
    expect(component.registerData.password).toBe('');
    expect(component.registerData.confirmPassword).toBe('');
  });

  it('should show error when fields are empty', () => {
    component.onSubmit();
    expect(component.errorMessage).toBe('Please fill in all fields.');
  });

  it('should show error for invalid email format', () => {
    component.registerData = { username: 'John', email: 'bad-email', password: 'Pass1234', confirmPassword: 'Pass1234', role: 'DEVELOPER' };
    component.onSubmit();
    expect(component.errorMessage).toBe('Please enter a valid email address.');
  });

  it('should show error when password is less than 8 chars', () => {
    component.registerData = { username: 'John', email: 'john@test.com', password: '1234567', confirmPassword: '1234567', role: 'DEVELOPER' };
    component.onSubmit();
    expect(component.errorMessage).toBe('Password must be at least 8 characters long.');
  });

  it('should show error when passwords do not match', () => {
    component.registerData = { username: 'John', email: 'john@test.com', password: 'Pass1234', confirmPassword: 'Pass5678', role: 'DEVELOPER' };
    component.onSubmit();
    expect(component.errorMessage).toBe('Password and Confirm Password must match.');
  });

  it('should call AuthService.register with valid data', () => {
    authServiceSpy.register.and.returnValue(of({ message: 'ok' }));

    component.registerData = { username: 'John', email: 'john@test.com', password: 'Pass1234', confirmPassword: 'Pass1234', role: 'DEVELOPER' };
    component.onSubmit();

    expect(authServiceSpy.register).toHaveBeenCalledWith({
      username: 'John',
      email: 'john@test.com',
      password: 'Pass1234',
      role: 'DEVELOPER'
    });
  });

  it('should show success message after registration', () => {
    authServiceSpy.register.and.returnValue(of({ message: 'ok' }));

    component.registerData = { username: 'John', email: 'john@test.com', password: 'Pass1234', confirmPassword: 'Pass1234', role: 'DEVELOPER' };
    component.onSubmit();

    expect(component.successMessage).toContain('Registration successful');
    expect(component.isLoading).toBeFalse();
  });

  it('should show duplicate email error on 409', () => {
    authServiceSpy.register.and.returnValue(throwError(() => ({ status: 409, error: { message: 'Email already exists' } })));

    component.registerData = { username: 'John', email: 'john@test.com', password: 'Pass1234', confirmPassword: 'Pass1234', role: 'DEVELOPER' };
    component.onSubmit();

    expect(component.errorMessage).toBe('Email already exists');
  });

  it('should show connection error on status 0', () => {
    authServiceSpy.register.and.returnValue(throwError(() => ({ status: 0 })));

    component.registerData = { username: 'John', email: 'john@test.com', password: 'Pass1234', confirmPassword: 'Pass1234', role: 'DEVELOPER' };
    component.onSubmit();

    expect(component.errorMessage).toBe('Unable to connect to server. Please check your connection.');
  });

  // --- Password strength ---
  it('should return 0 for empty password', () => {
    component.registerData.password = '';
    expect(component.getPasswordStrength()).toBe(0);
  });

  it('should return 25 for password with only length >= 8', () => {
    component.registerData.password = 'abcdefgh';
    expect(component.getPasswordStrength()).toBe(25);
  });

  it('should return 50 for password with length + uppercase', () => {
    component.registerData.password = 'Abcdefgh';
    expect(component.getPasswordStrength()).toBe(50);
  });

  it('should return 75 for password with length + uppercase + number', () => {
    component.registerData.password = 'Abcdefg1';
    expect(component.getPasswordStrength()).toBe(75);
  });

  it('should return 100 for password with length + uppercase + number + special', () => {
    component.registerData.password = 'Abcdef1!';
    expect(component.getPasswordStrength()).toBe(100);
  });

  // --- Toggle ---
  it('should toggle password visibility', () => {
    expect(component.showPassword).toBeFalse();
    component.togglePassword();
    expect(component.showPassword).toBeTrue();
  });

  it('should toggle confirm password visibility', () => {
    expect(component.showConfirmPassword).toBeFalse();
    component.toggleConfirmPassword();
    expect(component.showConfirmPassword).toBeTrue();
  });
});
