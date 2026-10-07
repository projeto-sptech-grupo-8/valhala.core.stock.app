package valhalla.core.stock.app.shared.pagination;

import org.springframework.data.domain.Sort;

public enum DirecaoOrdenacao {
    ASC,
    DESC;

    public Sort.Direction paraSpringData() {
        return Sort.Direction.valueOf(name());
    }
}
