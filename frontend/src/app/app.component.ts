import { Component, OnInit } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { CommonModule } from '@angular/common';
import { NavbarComponent } from './shared/navbar/navbar.component';
import { ToastComponent } from './shared/toast/toast.component';
import { ChatbotComponent } from './shared/chatbot/chatbot.component';
import { AuthService } from './services/auth.service';
import { Observable } from 'rxjs';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, NavbarComponent, ToastComponent, ChatbotComponent, CommonModule],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent implements OnInit {
  isLoggedIn$: Observable<boolean>;
  title = 'TaskFlow';

  constructor(private authService: AuthService) {
    this.isLoggedIn$ = this.authService.isLoggedIn$;
  }

  ngOnInit(): void {
    this.initializeTheme();
    this.initializeAccent();
  }

  private initializeTheme(): void {
    const savedTheme = localStorage.getItem('tf-theme') || 'light';
    document.documentElement.setAttribute('data-theme', savedTheme);
  }

  private initializeAccent(): void {
    const savedAccent = localStorage.getItem('tf-accent') || '#3B82F6';
    document.documentElement.style.setProperty('--tf-accent', savedAccent);
  }
}
