import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, tap } from 'rxjs';
import { Router } from '@angular/router';
import { environment } from '../../environments/environment';
import { auth } from '../config/firebase.config';
import { 
  createUserWithEmailAndPassword, 
  sendEmailVerification, 
  signInWithEmailAndPassword,
  onAuthStateChanged,
  User as FirebaseUser,
  getIdToken,
  sendPasswordResetEmail,
  confirmPasswordReset
} from 'firebase/auth';
import { AuthResponse, LoginRequest, RegisterRequest, User, UserRole } from '../models/user.model';
import { from, switchMap, catchError, throwError, of, interval, takeWhile, firstValueFrom } from 'rxjs';

const AUTH_API = `${environment.apiUrl}/auth`;
const TOKEN_KEY = 'auth_token';
const USER_KEY = 'auth_user';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private currentUserSubject = new BehaviorSubject<User | null>(this.getStoredUser());
  public currentUser$ = this.currentUserSubject.asObservable();

  private isLoggedInSubject = new BehaviorSubject<boolean>(this.hasToken());
  public isLoggedIn$ = this.isLoggedInSubject.asObservable();

  constructor(
    private http: HttpClient,
    private router: Router
  ) {
    this.checkTokenExpiry();
  }

  register(request: RegisterRequest): Observable<any> {
    // 1. Create user in Firebase
    return from(createUserWithEmailAndPassword(auth, request.email, request.password)).pipe(
      switchMap(userCredential => {
        const firebaseUser = userCredential.user;
        // 2. Send Email Verification through Firebase
        return from(sendEmailVerification(firebaseUser)).pipe(
          switchMap(() => {
            // 3. ALSO save to backend DB with real password (unverified)
            // This ensures the real password is stored so login works after verification
            return this.http.post(`${AUTH_API}/register`, request).pipe(
              catchError(err => {
                // If backend save fails (e.g. duplicate), still allow verification flow
                console.warn('Backend register sync failed:', err);
                return of({ message: 'Verification email sent' });
              })
            );
          })
        );
      })
    );
  }

  checkEmailExists(email: string): Observable<{ exists: boolean }> {
    return this.http.get<{ exists: boolean }>(`${AUTH_API}/check-email`, {
      params: { email }
    });
  }

  checkUsernameExists(username: string): Observable<{ exists: boolean }> {
    return this.http.get<{ exists: boolean }>(`${AUTH_API}/check-username`, {
      params: { username }
    });
  }

  sendPasswordResetEmail(email: string): Observable<void> {
    return from(sendPasswordResetEmail(auth, email));
  }

  confirmPasswordReset(code: string, newPassword: string): Observable<void> {
    return from(confirmPasswordReset(auth, code, newPassword));
  }

  // Returns true once user is verified on Firebase
  pollForVerification(): Observable<boolean> {
    return interval(1000).pipe(
      switchMap(async () => {
        const user = auth.currentUser;
        if (user) {
          await user.reload();
          return user.emailVerified;
        }
        return false;
      }),
      takeWhile(verified => !verified, true)
    );
  }

  // Finalize session by sending Firebase ID Token to backend
  finalizeFirebaseLogin(): Observable<AuthResponse> {
    const user = auth.currentUser;
    if (!user) return throwError(() => new Error('No firebase user found'));
    
    return from(getIdToken(user, true)).pipe(
      switchMap(token => {
        return this.http.post<AuthResponse>(`${AUTH_API}/firebase-login`, { token }).pipe(
          tap(response => this.processAuthResponse(response))
        );
      })
    );
  }

  // JUST Sync with backend (no session start)
  syncVerifiedUser(): Observable<AuthResponse> {
    const user = auth.currentUser;
    if (!user) return throwError(() => new Error('No firebase user found'));
    
    return from(getIdToken(user, true)).pipe(
      switchMap(token => {
        return this.http.post<AuthResponse>(`${AUTH_API}/firebase-login`, { token });
      })
    );
  }

  login(request: LoginRequest): Observable<AuthResponse> {
    // Priority: Check backend DB first (enables local accounts like Admin)
    return this.http.post<AuthResponse>(`${AUTH_API}/login`, request).pipe(
      tap(response => this.processAuthResponse(response))
    );
  }

  private processAuthResponse(response: AuthResponse): void {
    if (!response.token) {
      console.log('No token in response, skipping session start.');
      return;
    }
    this.storeToken(response.token);

    // Prefer role from response; fallback to JWT claim if not present
    let role = response.role;
    try {
      const payload = JSON.parse(atob(response.token.split('.')[1]));
      if (!role && payload?.role) {
        role = payload.role;
      }
    } catch {
      // ignore malformed token
    }

    const user: User = {
      id: response.id,
      username: response.username || '',
      email: response.email || '',
      role: role || 'MEMBER'
    };
    this.storeUser(user);
    this.currentUserSubject.next(user);
    this.isLoggedInSubject.next(true);
  }

  logout(): void {
    // Clear all chatbot history keys to ensure a fresh session
    Object.keys(sessionStorage).forEach(key => {
      if (key.startsWith('tf_chat_history_')) {
        sessionStorage.removeItem(key);
      }
    });
    Object.keys(localStorage).forEach(key => {
      if (key.startsWith('tf_chat_history_')) {
        localStorage.removeItem(key);
      }
    });

    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    this.currentUserSubject.next(null);
    this.isLoggedInSubject.next(false);
    this.router.navigate(['/login']);
  }

  getToken(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  }

  getUserRole(): UserRole {
    const user = this.getCurrentUser();
    return (user?.role as UserRole) || 'MEMBER';
  }

  isAdminOrManager(): boolean {
    const role = this.getUserRole();
    return role === 'ADMIN' || role === 'MANAGER';
  }

  isAuthenticated(): boolean {
    const token = this.getToken();
    if (!token) return false;
    // Check if token is expired
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      const expiry = payload.exp * 1000; // Convert to milliseconds
      if (Date.now() >= expiry) {
        this.logout();
        return false;
      }
      return true;
    } catch {
      return false;
    }
  }

  getCurrentUser(): User | null {
    return this.currentUserSubject.value;
  }

  private storeToken(token: string): void {
    localStorage.setItem(TOKEN_KEY, token);
  }

  private storeUser(user: User): void {
    localStorage.setItem(USER_KEY, JSON.stringify(user));
  }

  private getStoredUser(): User | null {
    const userStr = localStorage.getItem(USER_KEY);
    if (userStr) {
      try {
        return JSON.parse(userStr);
      } catch {
        return null;
      }
    }
    return null;
  }

  private hasToken(): boolean {
    return !!this.getToken();
  }

  private checkTokenExpiry(): void {
    if (this.hasToken() && !this.isAuthenticated()) {
      this.logout();
    }
  }
}
