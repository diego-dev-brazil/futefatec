package br.com.fatec.futefatec.servlet;

import java.io.IOException;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Filtro HTTP para desabilitar cache em respostas estáticas e dinâmicas.
 * Garante que atualizações visuais (ex: nome FUTFATEC) sejam refletidas
 * imediatamente sem necessidade de limpeza manual de cache no navegador.
 */
public class NoCacheFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (response instanceof HttpServletResponse httpResp) {
            httpResp.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
            httpResp.setHeader("Pragma", "no-cache");
            httpResp.setDateHeader("Expires", 0);
        }
        chain.doFilter(request, response);
    }
}

