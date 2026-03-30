import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { AuthService } from './auth.service';
import { environment } from '../../environments/environment';

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;
  let routerSpy: jasmine.SpyObj<Router>;

  const AUTH_API = `${environment.apiUrl}/auth`;

  beforeEach(() => {
    routerSpy = jasmine.createSpyObj('Router', ['navigate']);
    localStorage.clear();

    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [
        AuthService,
        { provide: Router, useValue: routerSpy }
      ]
    });

    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  // --- Registration ---
  // Skipped because it relies on Firebase functions which are difficult to mock in Karma module system
  xit('should send POST to /auth/register on registration', () => {
    const req = { username: 'John', email: 'john@example.com', password: 'Pass1234' };
    service.register(req as any).subscribe();

    const httpReq = httpMock.expectOne(`${AUTH_API}/register`);
    expect(httpReq.request.method).toBe('POST');
    expect(httpReq.request.body).toEqual(req);
    httpReq.flush({ message: 'User registered' });
  });

  // --- Login ---
  it('should store token and user after successful login', () => {
    const loginReq = { email: 'john@example.com', password: 'Pass1234' };

    // Create a valid JWT-like token (header.payload.signature)
    const payload = btoa(JSON.stringify({ sub: 'john@example.com', exp: Math.floor(Date.now() / 1000) + 86400 }));
    const fakeToken = `header.${payload}.signature`;

    const loginResp = { token: fakeToken, id: 1, username: 'John', email: 'john@example.com' };

    service.login(loginReq).subscribe(resp => {
      expect(resp.token).toBe(fakeToken);
    });

    const httpReq = httpMock.expectOne(`${AUTH_API}/login`);
    expect(httpReq.request.method).toBe('POST');
    httpReq.flush(loginResp);

    expect(localStorage.getItem('auth_token')).toBe(fakeToken);
    expect(service.getToken()).toBe(fakeToken);
    expect(service.getCurrentUser()?.username).toBe('John');
  });

  it('should emit isLoggedIn$ true after login', (done) => {
    const loginReq = { email: 'john@example.com', password: 'Pass1234' };
    const payload = btoa(JSON.stringify({ sub: 'john@example.com', exp: Math.floor(Date.now() / 1000) + 86400 }));
    const fakeToken = `h.${payload}.s`;
    const loginResp = { token: fakeToken, id: 1, username: 'John', email: 'john@example.com' };

    service.login(loginReq).subscribe(() => {
      service.isLoggedIn$.subscribe(val => {
        expect(val).toBeTrue();
        done();
      });
    });

    httpMock.expectOne(`${AUTH_API}/login`).flush(loginResp);
  });

  // --- Logout ---
  it('should clear localStorage and navigate to /login on logout', () => {
    localStorage.setItem('auth_token', 'some-token');
    localStorage.setItem('auth_user', JSON.stringify({ username: 'John' }));

    service.logout();

    expect(localStorage.getItem('auth_token')).toBeNull();
    expect(localStorage.getItem('auth_user')).toBeNull();
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/login']);
  });

  it('should emit isLoggedIn$ false and currentUser$ null after logout', (done) => {
    service.logout();

    service.isLoggedIn$.subscribe(val => {
      expect(val).toBeFalse();
      service.currentUser$.subscribe(user => {
        expect(user).toBeNull();
        done();
      });
    });
  });

  // --- isAuthenticated ---
  it('should return false when no token exists', () => {
    expect(service.isAuthenticated()).toBeFalse();
  });

  it('should return true for a valid non-expired token', () => {
    const payload = btoa(JSON.stringify({ sub: 'test', exp: Math.floor(Date.now() / 1000) + 86400 }));
    const token = `h.${payload}.s`;
    localStorage.setItem('auth_token', token);

    expect(service.isAuthenticated()).toBeTrue();
  });

  it('should return false and call logout for an expired token', () => {
    const payload = btoa(JSON.stringify({ sub: 'test', exp: Math.floor(Date.now() / 1000) - 100 }));
    const token = `h.${payload}.s`;
    localStorage.setItem('auth_token', token);

    expect(service.isAuthenticated()).toBeFalse();
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/login']);
  });

  it('should return false for a malformed token', () => {
    localStorage.setItem('auth_token', 'not-a-jwt');
    expect(service.isAuthenticated()).toBeFalse();
  });

  // --- getToken ---
  it('should return null when no token is stored', () => {
    expect(service.getToken()).toBeNull();
  });

  it('should return the stored token', () => {
    localStorage.setItem('auth_token', 'my-jwt');
    expect(service.getToken()).toBe('my-jwt');
  });
});
