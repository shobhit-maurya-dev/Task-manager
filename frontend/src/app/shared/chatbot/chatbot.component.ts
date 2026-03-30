import { Component, OnInit, AfterViewChecked, ElementRef, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ChatContextService } from '../../services/chat-context.service';
import { AiService } from '../../services/ai.service';
import { AuthService } from '../../services/auth.service';

interface Message {
  text: string;
  sender: 'user' | 'ai';
  timestamp: Date;
}

@Component({
  selector: 'app-chatbot',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './chatbot.component.html',
  styleUrl: './chatbot.component.css'
})
export class ChatbotComponent implements OnInit, AfterViewChecked {
  @ViewChild('messagesContainer') private messagesContainer!: ElementRef;

  isOpen = false;
  userInput = '';
  isThinking = false;
  private shouldScrollToBottom = false;

  messages: Message[] = [];
  private currentUserId: number | null = null;

  constructor(
    private chatContext: ChatContextService,
    private aiService: AiService,
    private authService: AuthService
  ) {}

  ngOnInit(): void {
    this.authService.currentUser$.subscribe(user => {
      this.currentUserId = user?.id || null;
      if (this.currentUserId) {
        this.loadHistory();
      } else {
        this.clearLocalHistory();
        this.messages = [];
      }
    });
  }

  private clearLocalHistory(): void {
    // Clear history from both localStorage (old) and sessionStorage
    if (this.currentUserId) {
      localStorage.removeItem(`tf_chat_history_${this.currentUserId}`);
      sessionStorage.removeItem(`tf_chat_history_${this.currentUserId}`);
    }
  }

  private loadHistory(): void {
    if (!this.currentUserId) return;
    const saved = sessionStorage.getItem(`tf_chat_history_${this.currentUserId}`);
    if (saved) {
      try {
        this.messages = JSON.parse(saved).map((m: any) => ({
          ...m,
          timestamp: new Date(m.timestamp)
        }));
      } catch (e) {
        this.setDefaultGreeting();
      }
    } else {
      this.setDefaultGreeting();
    }
  }

  private setDefaultGreeting(): void {
    this.messages = [
      {
        text: 'Hello! I am TaskFlow AI 🤖\n\nI know everything about your tasks, deadlines, and teams. Ask me anything!',
        sender: 'ai',
        timestamp: new Date()
      }
    ];
    this.saveHistory();
  }

  private saveHistory(): void {
    if (!this.currentUserId) return;
    sessionStorage.setItem(`tf_chat_history_${this.currentUserId}`, JSON.stringify(this.messages));
  }

  ngAfterViewChecked(): void {
    if (this.shouldScrollToBottom) {
      this.scrollToBottom();
      this.shouldScrollToBottom = false;
    }
  }

  toggleChat(): void {
    this.isOpen = !this.isOpen;
    if (this.isOpen) {
      this.shouldScrollToBottom = true;
    }
  }

  sendMessage(): void {
    const text = this.userInput.trim();
    if (!text || this.isThinking) return;

    this.messages.push({ text, sender: 'user', timestamp: new Date() });
    this.saveHistory(); // Save immediately
    this.userInput = '';
    this.isThinking = true;
    this.shouldScrollToBottom = true;

    // Try local search first (no extra DB call)
    setTimeout(() => {
      const localAnswer = this.chatContext.searchLocal(text);

      if (localAnswer) {
        this.isThinking = false;
        this.messages.push({ text: localAnswer, sender: 'ai', timestamp: new Date() });
        this.saveHistory();
        this.shouldScrollToBottom = true;
      } else {
        // No local match — Call Backend AI Fallback
        this.aiService.askGemini(text).subscribe({
          next: (res) => {
            this.isThinking = false;
            this.messages.push({ text: res.answer, sender: 'ai', timestamp: new Date() });
            this.saveHistory();
            this.shouldScrollToBottom = true;
          },
          error: (err) => {
            this.isThinking = false;
            console.error('AI Error:', err);
            this.messages.push({ 
              text: 'Oops! I encountered a network error while reaching the AI brain. Is the backend running? 🤔', 
              sender: 'ai', 
              timestamp: new Date() 
            });
            this.saveHistory();
            this.shouldScrollToBottom = true;
          }
        });
      }
    }, 800);
  }

  private scrollToBottom(): void {
    try {
      this.messagesContainer.nativeElement.scrollTop = this.messagesContainer.nativeElement.scrollHeight;
    } catch {}
  }
}
