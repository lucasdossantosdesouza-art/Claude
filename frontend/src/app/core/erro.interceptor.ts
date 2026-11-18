import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { catchError, throwError } from 'rxjs';

/** Exibe as mensagens de erro da API (ProblemDetail: title/detail) em um snackbar. */
export const erroInterceptor: HttpInterceptorFn = (req, next) => {
  const snack = inject(MatSnackBar);
  return next(req).pipe(
    catchError((e: HttpErrorResponse) => {
      const corpo = e.error;
      const msg =
        e.status === 0
          ? 'Não foi possível conectar ao servidor.'
          : e.status === 403
            ? 'Seu perfil não permite esta operação.'
            : (corpo?.detail ?? corpo?.title ?? e.message);
      snack.open(msg, 'Fechar', { duration: 7000, panelClass: 'snack-erro' });
      return throwError(() => e);
    }),
  );
};
