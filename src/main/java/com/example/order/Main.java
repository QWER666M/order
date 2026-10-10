package com.example.order;

import java.util.List;
import java.util.Scanner;

/**
 * 界面层：只负责"和用户交互"（打印菜单、读输入、显示结果）。
 * 业务判断全部交给 OrderService。
 */
public class Main {

    public static void main(String[] args) {
        OrderRepository repository = new OrderRepository();
        OrderService service = new OrderService(repository);
        Scanner scanner = new Scanner(System.in);

        printHelp();

        while (true) {
            System.out.print("请输入命令：");
            if (!scanner.hasNextLine()) {
                break;
            }
            String command = scanner.nextLine().trim();

            if (command.equals("1")) {
                createOrder(service, scanner);
            } else if (command.equals("2")) {
                listOrders(service);
            } else if (command.equals("3")) {
                payOrder(service, scanner);
            } else if (command.equals("4")) {
                cancelOrder(service, scanner);
            } else if (command.equals("5")) {
                showStatistics(service);
            }else if (command.equals("6")) {
                findOrder(service, scanner);
            } else if (command.equals("0")) {
                System.out.println("已退出");
                return;
            } else {
                System.out.println("没有这个命令，请重新输入");
            }
        }
    }

    private static void printHelp() {
        System.out.println("=== 订单管理 ===");
        System.out.println("1 创建订单   2 查看全部订单   3 支付订单");
        System.out.println("4 取消订单   5 查看统计       6 查询订单\r\n" + //
                        "0 退出");
    }

    private static void createOrder(OrderService service, Scanner scanner) {
        System.out.print("订单号：");
        String id = scanner.nextLine().trim();
        System.out.print("金额：");
        String amountText = scanner.nextLine().trim();

        try {
            int amount = Integer.parseInt(amountText);
            Order order = service.createOrder(id, amount);
            System.out.println("创建成功 -> " + order);
        } catch (NumberFormatException e) {
            System.out.println("金额必须是数字");
        } catch (IllegalArgumentException e) {
            System.out.println("创建失败：" + e.getMessage());
        }
    }

    private static void listOrders(OrderService service) {
        List<Order> orders = service.listOrders();
        if (orders.isEmpty()) {
            System.out.println("目前没有订单");
            return;
        }
        for (Order order : orders) {
            System.out.println(order);
        }
    }

    private static void payOrder(OrderService service, Scanner scanner) {
        System.out.print("要支付的订单号：");
        String id = scanner.nextLine().trim();

        if (service.pay(id)) {
            System.out.println("支付成功");
        } else {
            System.out.println("支付失败：订单不存在，或订单不是待支付状态");
        }
    }

    private static void cancelOrder(OrderService service, Scanner scanner) {
        System.out.print("要取消的订单号：");
        String id = scanner.nextLine().trim();

        if (service.cancel(id)) {
            System.out.println("取消成功");
        } else {
            System.out.println("取消失败：订单不存在，或订单不是待支付状态");
        }
    }

    private static void showStatistics(OrderService service) {
        System.out.println("待支付金额：" + service.sumAmount(OrderStatus.UNPAID));
        System.out.println("已支付金额：" + service.sumAmount(OrderStatus.PAID));
        System.out.println("已取消金额：" + service.sumAmount(OrderStatus.CANCELLED));
    }
    private static void findOrder(OrderService service, Scanner scanner) {
        System.out.print("请输入订单号：");
        String id = scanner.nextLine().trim();
        Order order = service.findOrder(id);
        if (order != null) {
            System.out.println("查询结果：" + order);
        } else {
            System.out.println("订单不存在");
        }
    }
}

