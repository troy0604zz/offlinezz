package com.example.aibi.query;

import com.example.aibi.common.BusinessException;
import com.example.aibi.config.AiBiProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SqlGuardTest {
    private final SqlGuard guard = new SqlGuard(new AiBiProperties(null,
            new AiBiProperties.Query(200, 1000, 5), null));

    @Test
    void acceptsControlledSelect() {
        var result = guard.validate("SELECT customer_id FROM customer LIMIT 5");
        assertThat(result.tables()).containsExactly("customer");
        var dateResult = guard.validate("SELECT EXTRACT(MONTH FROM order_date) AS month FROM sales_order");
        assertThat(dateResult.tables()).containsExactly("sales_order");
        var cteResult = guard.validate("WITH order_stats AS (SELECT customer_id FROM sales_order) SELECT * FROM order_stats");
        assertThat(cteResult.tables()).containsExactly("sales_order", "order_stats");
        var foundryResult = guard.validate("SELECT p.product_code, SUM(s.accepted_wafers) " +
                "FROM f360_product p JOIN f360_order_line ol ON ol.product_id=p.product_id " +
                "JOIN f360_shipment s ON s.order_line_id=ol.order_line_id GROUP BY p.product_code");
        assertThat(foundryResult.tables()).containsExactly("f360_product", "f360_order_line", "f360_shipment");
    }

    @Test
    void rejectsWriteAndMultipleStatements() {
        assertThatThrownBy(() -> guard.validate("DELETE FROM customer"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> guard.validate("SELECT 1; SELECT 2"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsUnknownTableAndDangerousFunction() {
        assertThatThrownBy(() -> guard.validate("SELECT * FROM secret_table"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> guard.validate("SELECT pg_sleep(10) FROM customer"))
                .isInstanceOf(BusinessException.class);
    }
}
